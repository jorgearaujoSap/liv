import { Component, computed, inject, signal } from '@angular/core';
import { ButtonComponent, DialogComponent } from 'liv-components';

import { IdleService } from '../../core/session/idle.service';

/**
 * Aviso de sesión por expirar (001-22, WCAG 2.2.1). Lee `IdleService.warning` y deja elegir:
 *
 * - por inactividad (2 minutos antes): «Seguir conectado» o «Cerrar sesión»;
 * - por el límite de 8 horas (5 minutos antes): solo «Cerrar sesión», porque seguir conectado no extiende
 *   un límite absoluto; el aviso da tiempo para guardar lo que se esté haciendo. **El texto de este segundo
 *   aviso no está en el diseño de Figma** (que solo muestra el de inactividad): usa la misma estructura.
 *
 * Diseño: Figma LIV-Diseño, `03 Pantallas`, «Aviso» (nodo 26:1157). No se cierra con Escape: la persona
 * elige una de las dos acciones.
 */
@Component({
  selector: 'app-session-warning-dialog',
  imports: [DialogComponent, ButtonComponent],
  templateUrl: './session-warning-dialog.html',
})
export class SessionWarningDialog {
  private readonly idle = inject(IdleService);

  protected readonly warning = this.idle.warning;
  protected readonly extending = signal(false);

  protected readonly heading = computed(() =>
    this.warning()?.kind === 'absolute'
      ? 'Tu sesión está por terminar'
      : 'Tu sesión está por expirar',
  );

  protected readonly description = computed(() => {
    const warning = this.warning();
    if (!warning) {
      return '';
    }
    // Los minutos se redondean hacia arriba: el texto solo cambia una vez por minuto, no cada segundo.
    const minutes = Math.max(1, Math.ceil(warning.secondsLeft / 60));
    const time = `${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}`;
    return warning.kind === 'absolute'
      ? `Por seguridad, ninguna sesión dura más de 8 horas. Cerraremos la tuya en ${time}. Guarda lo que estés haciendo y vuelve a iniciar sesión.`
      : `Por seguridad cerraremos tu sesión en ${time} por inactividad. ¿Quieres seguir conectado?`;
  });

  protected async keepConnected(): Promise<void> {
    if (this.extending()) {
      return;
    }
    this.extending.set(true);
    try {
      await this.idle.extend();
    } finally {
      this.extending.set(false);
    }
  }

  protected signOut(): void {
    void this.idle.logout();
  }
}
