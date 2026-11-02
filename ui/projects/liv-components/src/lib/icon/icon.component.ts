import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconName, IconSize, iconShapes } from './icons';

/**
 * Icono de Lucide (Figma: `Icon/<nombre>`). Hereda el color del texto (`currentColor`), así que se
 * colorea con una utilidad de `text-*` del contenedor. Sin `label` es decorativo y queda oculto para los
 * lectores de pantalla; con `label` se anuncia como imagen.
 */
@Component({
  selector: 'liv-icon',
  templateUrl: './icon.component.html',
  styleUrl: './icon.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input<IconSize>(24);
  /** Texto alternativo; si se omite, el icono es decorativo. */
  readonly label = input<string>();

  protected readonly shapes = computed(() => iconShapes(this.name()));
  /** Un texto vacío cuenta como ausente: el icono sigue siendo decorativo. */
  protected readonly accessibleName = computed(() => this.label()?.trim() || null);
}
