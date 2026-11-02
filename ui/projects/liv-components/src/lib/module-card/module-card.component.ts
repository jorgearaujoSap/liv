import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { BadgeComponent } from '../badge/badge.component';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { isPlainClick, linkTarget } from '../shared/link-click';

/**
 * Acceso a un módulo desde el inicio (Figma: `ModuleCard`). Toda la tarjeta es el enlace: con `href`, un
 * clic normal emite `navigate` con el destino (sin espacios sobrantes, pero sin sanear) y cancela la navegación del
 * navegador, para que la aplicación navegue con su enrutador y solo a rutas propias; con Ctrl, Cmd,
 * Mayús, Alt o el botón central el navegador abre el enlace como siempre. Un `href` ausente o vacío deja
 * la tarjeta inerte: una tarjeta habilitada necesita destino. Hover (borde `border/strong` y sombra
 * mayor) y Focus son los del navegador.
 *
 * `disabled` es un módulo aún no disponible: muestra la insignia (`disabledLabel`), no tiene `href`, no
 * navega, declara `aria-disabled="true"` y el clic no llega a quien escuche el anfitrión. La aplicación
 * debe mostrar solo los módulos que permiten las capacidades del usuario.
 */
@Component({
  selector: 'liv-module-card',
  imports: [BadgeComponent, IconComponent],
  templateUrl: './module-card.component.html',
  styleUrl: './module-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-disabled]': 'disabled() ? "" : null',
  },
})
export class ModuleCardComponent {
  /** Nombre del módulo (`Title` de Figma); se llama `heading` para no chocar con el atributo `title`. */
  readonly heading = input.required<string>();
  readonly description = input.required<string>();
  readonly icon = input.required<IconName>();
  /** Destino del módulo. */
  readonly href = input<string>();
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Texto de la insignia de un módulo deshabilitado. */
  readonly disabledLabel = input('Próximamente');
  /** Clic normal sobre una tarjeta habilitada: la aplicación navega a este destino. */
  readonly navigate = output<string>();

  protected readonly target = computed(() => linkTarget(this.href()));
  protected readonly descriptionText = computed(() => this.description().trim() || null);

  protected onClick(event: MouseEvent) {
    const href = this.target();
    if (this.disabled()) {
      event.preventDefault();
      event.stopPropagation();
    } else if (href && isPlainClick(event)) {
      event.preventDefault();
      this.navigate.emit(href);
    }
  }
}
