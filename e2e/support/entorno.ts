/**
 * Direcciones y datos del entorno local de desarrollo (`backend/dev`, ADR 0030). Los valores por omisión son los de
 * `backend/dev/.env.example`: ficticios y solo para el equipo de cada desarrollador. Se cambian con variables de
 * entorno si el entorno local usa otros.
 */
export const BASE_URL = process.env.LIV_E2E_BASE_URL ?? 'http://localhost:4200';
export const KEYCLOAK_URL = process.env.LIV_E2E_KEYCLOAK_URL ?? 'http://localhost:8180';
export const KEYCLOAK_ADMIN_USER = process.env.KEYCLOAK_ADMIN_USER ?? 'admin';
export const KEYCLOAK_ADMIN_PASSWORD = process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin-dev';

/** Contraseña de los usuarios ficticios de `backend/dev/keycloak/liv-realm-dev.yaml` y de los de estas pruebas. */
export const CLAVE = 'Liv-Dev-2026!';

/** Administración de Ipiales en la semilla de desarrollo (`backend/dev/seed`). */
export const IPIALES = '10000000-0000-0000-0000-000000052356';

/**
 * Usuarios que crean estas pruebas al empezar y borran al terminar (`preparar.ts`, `limpiar.ts`). No van en la capa
 * de desarrollo del realm: `keycloak-config-cli` duplicaría el TOTP al reaplicarla.
 */
export const USUARIOS_E2E = {
  /** Administrador con segundo factor ya configurado: Playwright calcula su código con `TOTP_SECRETO`. */
  totp: 'e2e.totp',
  /** Se bloquea a propósito en la prueba del bloqueo (001-04). */
  bloqueo: 'e2e.bloqueo',
} as const;

/** Secreto TOTP ficticio del usuario `e2e.totp`, solo para estas pruebas. */
export const TOTP_SECRETO = 'liv-e2e-totp-ficticio';
