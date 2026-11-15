import { join } from 'node:path';

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

/** «Administración de prueba B» en la misma semilla: la usan las pruebas de aislamiento entre administraciones. */
export const ADMINISTRACION_B = '10000000-0000-0000-0000-0000000b0001';

/**
 * Usuarios que crean estas pruebas al empezar y borran al terminar (`preparar.ts`, `limpiar.ts`). No van en la capa
 * de desarrollo del realm: `keycloak-config-cli` duplicaría el TOTP al reaplicarla.
 */
export const USUARIOS_E2E = {
  /** Administrador con segundo factor ya configurado: Playwright calcula su código con `TOTP_SECRETO`. */
  totp: 'e2e.totp',
  /** Se bloquea a propósito en la prueba del bloqueo (001-04). */
  bloqueo: 'e2e.bloqueo',
  /** Administrador sin segundo factor: lo configura en su primer ingreso (001-06). */
  alta: 'e2e.alta',
  /** Administrador de la administración de prueba B, con segundo factor ya configurado (feature 007). */
  adminB: 'e2e.admin.b',
} as const;

/** Secreto TOTP ficticio del usuario `e2e.totp`, solo para estas pruebas. */
export const TOTP_SECRETO = 'liv-e2e-totp-ficticio';

/** Secreto TOTP ficticio de `e2e.admin.b`: distinto del anterior, para que cada usuario lleve su propio intervalo. */
export const TOTP_SECRETO_B = 'liv-e2e-totp-ficticio-b';

/**
 * Sesiones guardadas por las pruebas con segundo factor que recorren varias pantallas (feature 007): entrar con TOTP
 * cuesta esperar al siguiente intervalo de 30 s, así que se entra una vez y las pruebas reutilizan la cookie. Están en
 * `resultados/`, que Playwright vacía en cada corrida y Git ignora.
 */
export const SESION_ADMIN_IPIALES = join(__dirname, '..', 'resultados', 'sesion-e2e-totp.json');
export const SESION_ADMIN_B = join(__dirname, '..', 'resultados', 'sesion-e2e-admin-b.json');
