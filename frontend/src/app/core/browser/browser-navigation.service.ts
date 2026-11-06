import { DOCUMENT, inject, Injectable } from '@angular/core';

/**
 * Navegación completa del navegador (fuera del router de Angular). Existe para poder probar, sin
 * descargar la página, el inicio y el cierre de sesión, que siempre recargan la aplicación (001-09).
 */
@Injectable({ providedIn: 'root' })
export class BrowserNavigation {
  private readonly document = inject(DOCUMENT);

  /**
   * Va a la dirección reemplazando la entrada actual del historial: «atrás» no vuelve a una pantalla
   * de la sesión que terminó.
   *
   * Si el navegador está precargando la página (p. ej. mientras se escribe la dirección), espera a que
   * la persona la abra: el BFF no abre un inicio de sesión desde una precarga y respondería 404.
   */
  replace(url: string): void {
    const go = () => this.document.defaultView?.location.replace(url);
    if ((this.document as Document & { prerendering?: boolean }).prerendering) {
      this.document.addEventListener('prerenderingchange', go, { once: true });
      return;
    }
    go();
  }
}
