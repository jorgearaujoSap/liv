import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

/** Estado que comunica la alerta. */
export type AlertStatus = 'error' | 'warning' | 'info' | 'success';

const ICONS: Record<AlertStatus, IconName> = {
  error: 'circle-alert',
  warning: 'triangle-alert',
  info: 'info',
  success: 'circle-check',
};

/**
 * Mensaje en línea con icono y fondo suave (Figma: `Alert`). El título (`heading`, la propiedad `Title`
 * de Figma) dice qué pasó y el mensaje, que se proyecta como contenido (`Message`), qué hacer. El
 * estado Error usa `role="alert"` y los demás `role="status"`. El icono es decorativo: el título o el
 * mensaje deben decir el estado, porque el color no es la única señal.
 *
 * Un lector de pantalla anuncia la alerta cuando aparece o cuando cambia su texto. Si un reintento
 * falla con el mismo mensaje, la pantalla debe volver a crear la alerta (p. ej. con un `@for` que
 * siga el número de intento) para que se anuncie otra vez.
 */
@Component({
  selector: 'liv-alert',
  imports: [IconComponent],
  templateUrl: './alert.component.html',
  styleUrl: './alert.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.role]': "status() === 'error' ? 'alert' : 'status'",
    '[attr.data-status]': 'status()',
  },
})
export class AlertComponent {
  readonly status = input.required<AlertStatus>();
  /** Título (`Show title` y `Title` de Figma); se llama `heading` para no chocar con el atributo `title`. */
  readonly heading = input<string>();

  protected readonly icon = computed(() => ICONS[this.status()]);
  protected readonly headingText = computed(() => this.heading()?.trim() || null);
}
