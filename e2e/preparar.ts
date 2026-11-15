import { BASE_URL, KEYCLOAK_URL } from './support/entorno';
import { prepararUsuarios } from './support/keycloak';
import { administracionesConVigencias, mensajeDeVigenciasPrevias } from './support/vigencias';

/**
 * Antes de las pruebas: comprueba que el sistema local esté arriba, que las administraciones de prueba no tengan
 * vigencias de una corrida anterior (feature 007) y crea los usuarios de las pruebas.
 */
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
  const conVigencias = await administracionesConVigencias();
  if (conVigencias.length > 0) {
    throw new Error(mensajeDeVigenciasPrevias(conVigencias));
  }
  await prepararUsuarios();
}
