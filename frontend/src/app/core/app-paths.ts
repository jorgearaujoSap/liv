/**
 * Rutas de la aplicación a las que llevan las guardas y el interceptor. Las pantallas que las atienden
 * (Sin permiso, Próximamente y Error) son de la tarea T22; aquí solo se fijan sus rutas.
 */
export const APP_PATHS = {
  forbidden: '/sin-permiso',
  comingSoon: '/proximamente',
  error: '/error',
} as const;

/** Ruta del BFF que inicia sesión (navegación completa, no una petición). */
export const LOGIN_PATH = '/bff/login';
