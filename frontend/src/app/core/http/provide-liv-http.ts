import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { EnvironmentProviders } from '@angular/core';

import { apiInterceptor } from './api.interceptor';

/**
 * `HttpClient` de la aplicación, con el interceptor y el token anti-CSRF (001-12): el BFF pone la
 * cookie `XSRF-TOKEN` (legible por el navegador, no es una credencial) y exige su valor en la cabecera
 * `X-XSRF-TOKEN` de toda petición que modifica datos.
 */
export function provideLivHttp(): EnvironmentProviders {
  return provideHttpClient(
    withInterceptors([apiInterceptor]),
    withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
  );
}
