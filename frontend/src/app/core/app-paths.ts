/**
 * Rutas de la aplicación a las que llevan las guardas, el interceptor y las pantallas (T22). Aquí se fijan
 * una sola vez para que la ruta, el menú y las guardas hablen de lo mismo.
 */
export const APP_PATHS = {
  home: '/',
  forbidden: '/sin-permiso',
  comingSoon: '/proximamente',
  error: '/error',
} as const;

/**
 * Prefijo de la ruta de una opción del menú: `/opcion/1.5.9`. Lleva el número de Doc. 01 §3 para que la
 * ruta y la documentación hablen de lo mismo.
 */
export const OPTION_ROUTE = 'opcion';

/** Ruta del BFF que inicia sesión (navegación completa, no una petición). */
export const LOGIN_PATH = '/bff/login';
