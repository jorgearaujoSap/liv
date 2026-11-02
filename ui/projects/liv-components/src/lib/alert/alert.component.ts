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
 * estado Error usa `role="alert"` y los demás `role="status"`; el icono es decorativo porque el texto
 * dice el estado.
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
