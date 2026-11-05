import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, Observable, switchMap, throwError } from 'rxjs';

import { APP_PATHS } from '../app-paths';
import { CapabilityService } from '../capabilities/capability.service';
import { SessionService } from '../session/session.service';
import { RETRIED_AFTER_UNAUTHORIZED, SKIP_FORBIDDEN_HANDLING } from './api-context';
import { hasStatus } from './http-errors';

/** Código del criterio 001-12: el BFF rechazó la petición por falta del token anti-CSRF. */
const CSRF_REJECTED = '001-12';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** Una petición a una ruta de este mismo sitio (`/api/...`, `/bff/...`), nunca a otro origen. */
function isOwnRequest(request: HttpRequest<unknown>): boolean {
  return /^\/(?![/\\])/.test(request.url);
}

/** Solo se reintenta lo que no cambia nada si llega dos veces: lecturas o peticiones con `Idempotency-Key`. */
function canRetry(request: HttpRequest<unknown>): boolean {
  return SAFE_METHODS.has(request.method) || request.headers.has('Idempotency-Key');
}

/**
 * Interceptor de las peticiones de la aplicación (feature 001, T21):
 *
 * - agrega `X-Correlation-Id` (un UUID por petición) a lo que va a este mismo sitio;
 * - en `/api/**`, ante un 401 consulta `/bff/session`. Sin sesión, va a iniciar sesión. Con sesión,
 *   reintenta **una vez y solo** una lectura o una petición con `Idempotency-Key` (001-01); lo demás
 *   también va a iniciar sesión, porque repetirlo podría duplicar un efecto;
 * - en `/api/**`, ante un 403 recarga las capacidades y lleva a «sin permiso» (001-16), salvo que el
 *   403 sea el rechazo anti-CSRF (001-12), que no es falta de permiso.
 *
 * El token anti-CSRF no se agrega aquí: `withXsrfConfiguration` (ver `provideLivHttp`) copia la cookie
 * `XSRF-TOKEN` a `X-XSRF-TOKEN` en toda petición que modifica datos.
 */
export const apiInterceptor: HttpInterceptorFn = (original, next) => {
  const session = inject(SessionService);
  const capabilities = inject(CapabilityService);
  const router = inject(Router);

  if (!isOwnRequest(original)) {
    return next(original);
  }
  const isApi = original.url.startsWith('/api/');

  const toLogin = (error: unknown): Observable<never> => {
    session.redirectToLogin(router.url);
    return throwError(() => error);
  };

  const recoverFromUnauthorized = (
    request: HttpRequest<unknown>,
    error: HttpErrorResponse,
  ): Observable<HttpEvent<unknown>> => {
    if (request.context.get(RETRIED_AFTER_UNAUTHORIZED)) {
      return toLogin(error);
    }
    return from(session.status().catch(() => undefined)).pipe(
      switchMap((status) => {
        if (status === undefined) {
          // No se pudo averiguar: no se inicia sesión ni se reintenta a ciegas.
          return throwError(() => error);
        }
        if (status === null || !canRetry(request)) {
          return toLogin(error);
        }
        return send(
          request.clone({ context: request.context.set(RETRIED_AFTER_UNAUTHORIZED, true) }),
        );
      }),
    );
  };

  const recoverFromForbidden = (
    request: HttpRequest<unknown>,
    error: HttpErrorResponse,
  ): Observable<never> => {
    const rejectedByCsrf = (error.error as { code?: unknown } | null)?.code === CSRF_REJECTED;
    if (rejectedByCsrf || request.context.get(SKIP_FORBIDDEN_HANDLING)) {
      return throwError(() => error);
    }
    return from(
      (async () => {
        await capabilities.reload().catch(() => undefined);
        if (router.url !== APP_PATHS.forbidden) {
          await router.navigateByUrl(APP_PATHS.forbidden).catch(() => undefined);
        }
      })(),
    ).pipe(switchMap(() => throwError(() => error)));
  };

  const send = (request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> =>
    next(request).pipe(
      catchError((error: unknown) => {
        if (isApi && hasStatus(error, 401)) {
          return recoverFromUnauthorized(request, error as HttpErrorResponse);
        }
        if (isApi && hasStatus(error, 403)) {
          return recoverFromForbidden(request, error as HttpErrorResponse);
        }
        return throwError(() => error);
      }),
    );

  return send(
    original.headers.has('X-Correlation-Id')
      ? original
      : original.clone({ setHeaders: { 'X-Correlation-Id': crypto.randomUUID() } }),
  );
};
