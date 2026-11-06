import { Component, inject } from '@angular/core';
import { ButtonComponent, LogoComponent, ResultStateComponent } from 'liv-components';

import { APP_PATHS } from '../../core/app-paths';
import { BrowserNavigation } from '../../core/browser/browser-navigation.service';
import { SessionService } from '../../core/session/session.service';

/**
 * Error al cargar la sesión: no se pudo saber si hay sesión ni cargar quién es la persona. Diseño: Figma
 * LIV-Diseño, `03 Pantallas`, «Error» (nodo 26:1672). Pantalla completa, sin la estructura general: sale
 * justo cuando no hay con qué armarla, y no exige sesión.
 */
@Component({
  selector: 'app-load-error',
  imports: [LogoComponent, ResultStateComponent, ButtonComponent],
  templateUrl: './load-error.html',
  styleUrl: './load-error.css',
})
export class LoadError {
  private readonly navigation = inject(BrowserNavigation);
  private readonly session = inject(SessionService);

  /** Vuelve a cargar la aplicación desde el inicio: la guarda de sesión vuelve a preguntar. */
  protected retry(): void {
    this.navigation.replace(APP_PATHS.home);
  }

  /** Aquí no hay `IdleService` (nadie vigila una sesión que no se pudo cargar): se cierra con `SessionService`. */
  protected signOut(): void {
    void this.session.logout();
  }
}
