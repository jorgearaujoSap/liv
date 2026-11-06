/** La primera parte de la ruta es del BFF o de la API: no es una pantalla a la que volver. */
const SERVER_ROUTES = /^\/(bff|api|internal|login\/oauth2|oauth2)(?:[/?#]|$)/;

function hasControlCharacter(path: string): boolean {
  return [...path].some((char) => {
    const code = char.charCodeAt(0);
    return code < 0x20 || code === 0x7f;
  });
}

/**
 * Una ruta propia de este sitio: empieza por una sola `/`, sin `\` ni caracteres de control. Los
 * navegadores quitan los tabuladores y los saltos de línea de una dirección antes de leerla, así que
 * `/<tab>/otro.sitio` se convertiría en `//otro.sitio`, una dirección de otro sitio.
 */
export function isOwnPath(path: string): boolean {
  return /^\/(?![/\\])[^\\]*$/.test(path) && !hasControlCharacter(path);
}

/**
 * Una ruta a la que se puede volver tras iniciar sesión: propia y de la aplicación, no del BFF ni de la
 * API. El BFF repite esta validación (`returnTo`); aquí se evita pedirle algo que va a rechazar.
 */
export function isReturnTarget(path: string): boolean {
  return isOwnPath(path) && !SERVER_ROUTES.test(path);
}
