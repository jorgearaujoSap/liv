/** Clic principal sin teclas modificadoras: el que la aplicación debe resolver con su enrutador. */
export function isPlainClick(event: MouseEvent): boolean {
  return event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey;
}

/** El destino sin espacios, o `null` si está ausente o vacío: un `href=""` recargaría la página. */
export function linkTarget(href: string | undefined): string | null {
  return href?.trim() || null;
}
