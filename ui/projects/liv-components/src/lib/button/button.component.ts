import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { SpinnerComponent } from '../spinner/spinner.component';

/** Énfasis del botón: `primary` es la acción principal (una por vista); `ghost`, la de menor énfasis. */
export type ButtonVariant = 'primary' | 'secondary' | 'ghost';

/** Tamaño: `md` (40 px) en la aplicación; `lg` (48 px) en los formularios de acceso y en móvil. */
export type ButtonSize = 'md' | 'lg';

/** Tipo del botón nativo. Por defecto `button`, para no enviar formularios por accidente. */
export type ButtonType = 'button' | 'submit' | 'reset';

/**
 * Botón (Figma: `Button`). El texto se proyecta como contenido (la propiedad `Label` de Figma) y el
 * clic se escucha con `(click)` sobre `liv-button`. Los estados Hover y Focus son los del navegador
 * (`:hover` y `:focus-visible`).
 *
 * En `loading` muestra el spinner en lugar del icono inicial, no emite clic ni envía el formulario y
 * sigue enfocable (`aria-disabled`), para que el foco no se pierda. `loadingLabel` (p. ej.
 * "Ingresando…") se anuncia en una región viva que existe desde el inicio, fuera del botón; si la
 * pantalla también cambia el texto visible, debe usar el mismo.
 */
@Component({
  selector: 'liv-button',
  imports: [IconComponent, SpinnerComponent],
  templateUrl: './button.component.html',
  styleUrl: './button.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-variant]': 'variant()',
    '[attr.data-size]': 'size()',
  },
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<ButtonType>('button');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly loading = input(false, { transform: booleanAttribute });
  /** Icono al inicio (`Show leading icon` y `Leading icon` de Figma); sin él, no se dibuja. */
  readonly leadingIcon = input<IconName>();
  /** Icono al final (`Show trailing icon` y `Trailing icon` de Figma). */
  readonly trailingIcon = input<IconName>();
  /** Qué se anuncia mientras carga, p. ej. "Ingresando…". */
  readonly loadingLabel = input('Cargando');

  protected readonly iconSize = computed(() => (this.size() === 'lg' ? 20 : 16));
  protected readonly spinnerTone = computed(() =>
    this.variant() === 'primary' ? 'inverse' : 'default',
  );
  /** Ocupado solo si no está deshabilitado: el estado Disabled prevalece. */
  protected readonly busy = computed(() => this.loading() && !this.disabled());

  protected onClick(event: MouseEvent): void {
    if (this.busy()) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }
}
