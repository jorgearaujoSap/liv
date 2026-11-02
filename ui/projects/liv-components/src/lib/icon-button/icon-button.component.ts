import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { ButtonType } from '../button/button.component';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

/** Énfasis del botón de icono: `ghost` sin fondo; `secondary` con fondo y borde. */
export type IconButtonVariant = 'ghost' | 'secondary';

/** Tamaño: `sm` (32 px, icono de 16) o `md` (40 px, icono de 20). */
export type IconButtonSize = 'sm' | 'md';

/**
 * Botón de solo icono (Figma: `IconButton`). Siempre tiene `label`: es su nombre accesible
 * (`aria-label`) y su tooltip, en español (p. ej. "Mostrar contraseña"). El clic se escucha con
 * `(click)` sobre `liv-icon-button`. En pantallas táctiles el área de toque crece hasta 44 px sin
 * cambiar el dibujo; el margen alrededor lo da quien lo usa.
 */
@Component({
  selector: 'liv-icon-button',
  imports: [IconComponent],
  templateUrl: './icon-button.component.html',
  styleUrl: './icon-button.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-variant]': 'variant()',
    '[attr.data-size]': 'size()',
  },
})
export class IconButtonComponent {
  readonly icon = input.required<IconName>();
  /** Qué hace el botón; es el `aria-label` y el tooltip. */
  readonly label = input.required<string>();
  readonly variant = input<IconButtonVariant>('ghost');
  readonly size = input<IconButtonSize>('sm');
  readonly type = input<ButtonType>('button');
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly iconSize = computed(() => (this.size() === 'md' ? 20 : 16));
}
