import { BASE_URL, KEYCLOAK_URL } from './support/entorno';
import { prepararUsuarios } from './support/keycloak';

/** Antes de las pruebas: comprueba que el sistema local esté arriba y crea los usuarios de las pruebas. */
export default async function preparar(): Promise<void> {
  for (const [nombre, url] of [
    ['el frontend', BASE_URL],
    ['Keycloak', `${KEYCLOAK_URL}/realms/liv/.well-known/openid-configuration`],
  ] as const) {
    try {
      await fetch(url, { redirect: 'manual' });
    } catch {
      throw new Error(
        `No responde ${nombre} en ${url}. Levanta el sistema como dice README.md › «Ejecutar LIV en local» ` +
          'y vuelve a correr las pruebas.',
      );
    }
  }
  await prepararUsuarios();
}
