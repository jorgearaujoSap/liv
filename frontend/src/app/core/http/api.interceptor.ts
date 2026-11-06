import {
  HttpContext,
  HttpErrorResponse,
  HttpEvent,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, Observable, switchMap, throwError } from 'rxjs';

import { APP_PATHS } from '../app-paths';
import { CapabilityService } from '../capabilities/capability.service';
import { currentUrl } from '../current-url';
import { isOwnPath } from '../own-path';
import { SessionService } from '../session/session.service';
import { RETRIED_AFTER_UNAUTHORIZED, SKIP_FORBIDDEN_HANDLING } from './api-context';
import { hasStatus } from './http-errors';

/** Código del criterio 001-12: el BFF rechazó la petición por falta del token anti-CSRF. */
const CSRF_REJECTED = '001-12';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

type Send = (request: HttpRequest<unknown>) => Observable<HttpEvent<unknown>>;

interface Dependencies {
  readonly session: SessionService;
  readonly capabilities: CapabilityService;
  readonly router: Router;
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
  const dependencies: Dependencies = {
    session: inject(SessionService),
    capabilities: inject(CapabilityService),
    router: inject(Router),
  };

  if (!isOwnPath(original.url)) {
    return next(original);
  }
  const isApi = original.url.startsWith('/api/');

  const send: Send = (request) =>
    next(request).pipe(
      catchError((error: unknown) => {
        if (isApi && hasStatus(error, 401)) {
          return recoverFromUnauthorized(dependencies, request, error as HttpErrorResponse, send);
        }
        if (isApi && hasStatus(error, 403)) {
          return recoverFromForbidden(dependencies, request, error as HttpErrorResponse);
        }
        return throwError(() => error);
      }),
    );

  return send(withCorrelationId(original));
};

function withCorrelationId(request: HttpRequest<unknown>): HttpRequest<unknown> {
  const id = request.headers.has('X-Correlation-Id') ? undefined : newCorrelationId();
  return id ? request.clone({ setHeaders: { 'X-Correlation-Id': id } }) : request;
}

/**
 * Un UUID v4. `crypto.randomUUID` solo existe en páginas seguras (HTTPS o localhost): en otro contexto se
 * arma con `getRandomValues`. El identificador es solo de trazabilidad y nunca debe impedir la petición.
 */
function newCorrelationId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Solo se reintenta lo que no cambia nada si llega dos veces: lecturas o peticiones con `Idempotency-Key`. */
function canRetry(request: HttpRequest<unknown>): boolean {
  return SAFE_METHODS.has(request.method) || request.headers.has('Idempotency-Key');
}

/** Lo mismo, pero marcada como ya reintentada. Con un contexto nuevo: el de quien llamó no se toca. */
function markedAsRetried(request: HttpRequest<unknown>): HttpRequest<unknown> {
  const context = new HttpContext();
  for (const key of request.context.keys()) {
    context.set(key, request.context.get(key));
  }
  return request.clone({ context: context.set(RETRIED_AFTER_UNAUTHORIZED, true) });
}

/** `sessionEnded`: solo si se comprobó que no hay sesión se avisa a las demás pestañas. */
function toLogin(
  { session, router }: Dependencies,
  error: unknown,
  sessionEnded: boolean,
): Observable<never> {
  session.redirectToLogin(currentUrl(router), { sessionEnded });
  return throwError(() => error);
}

function recoverFromUnauthorized(
  dependencies: Dependencies,
  request: HttpRequest<unknown>,
  error: HttpErrorResponse,
  send: Send,
): Observable<HttpEvent<unknown>> {
  if (request.context.get(RETRIED_AFTER_UNAUTHORIZED)) {
    // La sesión estaba abierta al reintentar; no se sabe si terminó.
    return toLogin(dependencies, error, false);
  }
  // `undefined`: no se pudo averiguar si hay sesión. `null`: se averiguó y no hay.
  return from(dependencies.session.status().catch(() => undefined)).pipe(
    switchMap((status) => {
      if (status === undefined) {
        // No se inicia sesión ni se reintenta a ciegas.
        return throwError(() => error);
      }
      if (status === null || !canRetry(request)) {
        return toLogin(dependencies, error, status === null);
      }
      return send(markedAsRetried(request));
    }),
  );
}

/**
 * El cuerpo del 403 es un Problem Details. Si la respuesta se esperaba como archivo (`blob`), bytes o
 * texto, el cuerpo no está leído: se lee para poder distinguir el rechazo anti-CSRF.
 */
async function isCsrfRejection(error: HttpErrorResponse): Promise<boolean> {
  let body: unknown = error.error;
  try {
    if (body instanceof Blob) {
      body = await body.text();
    } else if (Object.prototype.toString.call(body) === '[object ArrayBuffer]') {
      body = new TextDecoder().decode(body as ArrayBuffer);
    }
    if (typeof body === 'string') {
      body = JSON.parse(body);
    }
  } catch {
    return false;
  }
  return (body as { code?: unknown } | null)?.code === CSRF_REJECTED;
}

function recoverFromForbidden(
  { capabilities, router }: Dependencies,
  request: HttpRequest<unknown>,
  error: HttpErrorResponse,
): Observable<never> {
  if (request.context.get(SKIP_FORBIDDEN_HANDLING)) {
    return throwError(() => error);
  }
  return from(
    (async () => {
      if (await isCsrfRejection(error)) {
        return;
      }
      await capabilities.reload().catch(() => undefined);
      if (router.url !== APP_PATHS.forbidden) {
        await router.navigateByUrl(APP_PATHS.forbidden).catch(() => undefined);
      }
    })(),
  ).pipe(switchMap(() => throwError(() => error)));
}
