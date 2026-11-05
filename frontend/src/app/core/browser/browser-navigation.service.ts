import { Injectable } from '@angular/core';

/**
 * Navegación completa del navegador (fuera del router de Angular). Existe para poder probar, sin
 * descargar la página, el inicio y el cierre de sesión, que siempre recargan la aplicación (001-09).
 */
@Injectable({ providedIn: 'root' })
export class BrowserNavigation {
  /**
   * Va a la dirección reemplazando la entrada actual del historial: «atrás» no vuelve a una pantalla
   * de la sesión que terminó.
   */
  replace(url: string): void {
    window.location.replace(url);
  }
}
