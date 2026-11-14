import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { SpinnerComponent } from '../spinner/spinner.component';

let nextId = 0;

/** Énfasis del botón: `primary` es la acción principal (una por vista); `ghost`, la de menor énfasis. */
export type ButtonVariant = 'primary' | 'secondary' | 'ghost';

/** Tamaño: `md` (40 px) en la aplicación; `lg` (48 px) en los formularios de acceso y en móvil. */
export type ButtonSize = 'md' | 'lg';

/** Alineación del motivo de la deshabilitación: al inicio o al final del botón. */
export type ReasonAlign = 'start' | 'end';

/** Tipo del botón nativo. Por defecto `button`, para no enviar formularios por accidente. */
export type ButtonType = 'button' | 'submit' | 'reset';

/**
 * Botón (Figma: `Button`). El texto se proyecta como contenido (la propiedad `Label` de Figma) y el
 * clic se escucha con `(click)` sobre `liv-button`. Los estados Hover y Focus son los del navegador
 * (`:hover` y `:focus-visible`).
 *
 * En `loading` muestra el spinner en lugar del icono inicial, no emite clic ni envía el formulario y
 * sigue enfocable (`aria-disabled`), para que el foco no se pierda. Como en Figma, el texto visible no
 * cambia: lo que se anuncia es `loadingLabel` (p. ej. "Ingresando…"), en una región viva que existe
 * desde el inicio, fuera del botón. `disabled` prevalece sobre `loading`, así que no se ata al mismo
 * estado pendiente: el doble envío lo evita `loading`.
 *
 * `disabledReason` explica por qué está deshabilitado: solo cuenta con `disabled` y un texto no vacío.
 * El motivo se muestra debajo del botón, alineado según `reasonAlign`, y el botón lo enlaza con
 * `aria-describedby`. Con motivo, el botón usa `aria-disabled="true"` en lugar de `disabled`, para seguir
 * recibiendo el foco del teclado y que el lector de pantalla anuncie el motivo; no emite clic ni envía
 * el formulario (mismo guarda que en `loading`). El aspecto es el de siempre: lo da `data-state="disabled"`
 * en el anfitrión, que también tiene el botón deshabilitado sin motivo. Con motivo, el botón se ajusta a
 * su contenido en lugar de ocupar todo el ancho del anfitrión, y se alinea al inicio o al final.
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
    '[attr.data-state]': 'disabled() ? "disabled" : null',
    '[attr.data-reason]': 'reason() ? "" : null',
    '[attr.data-reason-align]': 'reasonAlign()',
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
  /**
   * Por qué está deshabilitado (texto visible debajo del botón). Solo cuenta con `disabled`; sin texto
   * o con solo espacios no se muestra y el botón usa `disabled`.
   */
  readonly disabledReason = input<string>();
  /** Alineación del motivo y del botón: `start` (por omisión) o `end`. */
  readonly reasonAlign = input<ReasonAlign>('start');
  /** Qué se anuncia mientras carga, p. ej. "Ingresando…". */
  readonly loadingLabel = input('Cargando');

  protected readonly iconSize = computed(() => (this.size() === 'lg' ? 20 : 16));
  protected readonly spinnerTone = computed(() =>
    this.variant() === 'primary' ? 'inverse' : 'default',
  );
  /** El motivo que se muestra: solo con `disabled` y con texto. */
  protected readonly reason = computed(() =>
    this.disabled() ? (this.disabledReason()?.trim() ?? '') : '',
  );
  protected readonly reasonId = `liv-button-reason-${nextId++}`;
  /** Ocupado solo si no está deshabilitado: el estado Disabled prevalece. */
  protected readonly busy = computed(() => this.loading() && !this.disabled());

  constructor() {
    // En fase de captura sobre el anfitrión, para correr antes que el `(click)` que la pantalla pone en
    // liv-button y cubrir el clic sobre el botón, el anfitrión o la región viva, también si se despacha
    // por código. `preventDefault` evita además el envío del formulario.
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const guard = (event: Event) => {
      if (this.busy() || this.disabled()) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    };
    host.addEventListener('click', guard, { capture: true });
    inject(DestroyRef).onDestroy(() => host.removeEventListener('click', guard, { capture: true }));
  }
}
