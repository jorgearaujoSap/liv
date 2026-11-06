import { limpiarUsuarios } from './support/keycloak';

/** Después de las pruebas: borra los usuarios que creó `preparar.ts`. */
export default async function limpiar(): Promise<void> {
  await limpiarUsuarios();
}
