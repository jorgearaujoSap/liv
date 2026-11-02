import {
  afterNextRender,
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  viewChild,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

let nextId = 0;

/**
 * Diálogo modal (Figma: `Dialog`) sobre `bg/overlay`, p. ej. el aviso de sesión por expirar que exige
 * WCAG 2.2.1. `open` lo controla la pantalla; el diálogo no se cierra solo. Las acciones se proyectan
 * como contenido, en el orden en que se leen (la secundaria primero).
 *
 * Al abrirse, el foco va al elemento marcado con `data-initial-focus` (o a su primer enfocable) o, si no lo hay,
 * al primer enfocable; el foco queda atrapado adentro y, al cerrarse, vuelve a quien lo tenía. Escape
 * emite `closed` si `dismissible` es verdadero; en acciones críticas, pásalo en falso. Hacer clic fuera
 * no cierra, para no perder lo que el usuario estaba haciendo.
 *
 * El diálogo no vuelve inerte el resto de la página: lo atrapa el foco y `aria-modal` lo oculta a los
 * lectores de pantalla. Debe haber un solo diálogo abierto a la vez.
 */
@Component({
  selector: 'liv-dialog',
  imports: [IconComponent],
  templateUrl: './dialog.component.html',
  styleUrl: './dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogComponent {
  readonly open = input(false, { transform: booleanAttribute });
  /** Título (`Title` de Figma); se llama `heading` para no chocar con el atributo `title`. */
  readonly heading = input.required<string>();
  readonly description = input<string>();
  /** Icono del círculo (`Icon` de Figma); por defecto, `history`. */
  readonly icon = input<IconName>();
  /** Si Escape cierra el diálogo. */
  readonly dismissible = input(true, { transform: booleanAttribute });
  /** Escape pidió cerrar: la pantalla decide si lo hace. */
  readonly closed = output<void>();

  private readonly uid = nextId++;
  protected readonly titleId = `liv-dialog-title-${this.uid}`;
  protected readonly descriptionId = `liv-dialog-description-${this.uid}`;
  protected readonly iconName = computed<IconName>(() => this.icon() ?? 'history');
  protected readonly descriptionText = computed(() => this.description()?.trim() || null);

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly injector = inject(Injector);

  constructor() {
    effect((onCleanup) => {
      if (this.open()) {
        onCleanup(this.trapFocus());
      }
    });
  }

  /**
   * Mueve el foco adentro, escucha el teclado y los cambios de foco, y devuelve la función que lo deshace
   * y devuelve el foco a quien lo tenía.
   */
  private trapFocus(): () => void {
    const previous = document.activeElement;
    const onKeydown = (event: KeyboardEvent) => this.onKeydown(event);
    const onFocusin = (event: FocusEvent) => this.onFocusin(event);
    document.addEventListener('keydown', onKeydown);
    document.addEventListener('focusin', onFocusin);
    afterNextRender(() => this.focusInitial(), { injector: this.injector });
    return () => {
      document.removeEventListener('keydown', onKeydown);
      document.removeEventListener('focusin', onFocusin);
      if (previous instanceof HTMLElement) {
        previous.focus();
      }
    };
  }

  private onKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape' && this.dismissible()) {
      event.preventDefault();
      this.closed.emit();
    } else if (event.key === 'Tab') {
      this.trapTab(event);
    }
  }

  /** Si el foco llega a algo fuera del diálogo, lo devuelve adentro. */
  private onFocusin(event: FocusEvent) {
    const panel = this.panel()?.nativeElement;
    if (panel && event.target instanceof Node && !panel.contains(event.target)) {
      this.focusInitial();
    }
  }

  /** Tab en el último enfocable salta al primero y Mayús+Tab en el primero salta al último. */
  private trapTab(event: KeyboardEvent) {
    const panel = this.panel()?.nativeElement;
    if (!panel) {
      return;
    }
    const focusables = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE));
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (!first || !panel.contains(document.activeElement)) {
      event.preventDefault();
      (first ?? panel).focus();
    } else if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private focusInitial() {
    const panel = this.panel()?.nativeElement;
    if (panel) {
      this.initialFocusTarget(panel).focus();
    }
  }

  /** El elemento marcado con `data-initial-focus` (o su primer enfocable), el primer enfocable o el panel. */
  private initialFocusTarget(panel: HTMLElement): HTMLElement {
    const marked = panel.querySelector<HTMLElement>('[data-initial-focus]');
    if (marked?.matches(FOCUSABLE)) {
      return marked;
    }
    return (
      marked?.querySelector<HTMLElement>(FOCUSABLE) ??
      panel.querySelector<HTMLElement>(FOCUSABLE) ??
      panel
    );
  }
}
