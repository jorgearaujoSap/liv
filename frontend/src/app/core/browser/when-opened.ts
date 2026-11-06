/**
 * Se cumple cuando la persona tiene la página delante. Si el navegador la está precargando (p. ej.
 * mientras se escribe la dirección), espera a que la abra (`prerenderingchange`); si nunca la abre, el
 * navegador la descarta y no pasa nada. En un navegador sin precarga se cumple de inmediato.
 *
 * Con esto la aplicación no arranca durante la precarga: no pide nada al BFF ni avisa a otras pestañas, y
 * decide si hay sesión con lo que hay al abrirla (001-01). El BFF, además, no abre un inicio de sesión
 * desde una precarga: respondería 404.
 */
export function whenOpened(document: Document): Promise<void> {
  if (!(document as Document & { prerendering?: boolean }).prerendering) {
    return Promise.resolve();
  }
  return new Promise((resolve) =>
    document.addEventListener('prerenderingchange', () => resolve(), { once: true }),
  );
}
