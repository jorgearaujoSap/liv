import { DOCUMENT } from '@angular/common';
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

const FORM_CONTROL = /^(BUTTON|INPUT|SELECT|TEXTAREA)$/;

/** Ancho del panel (`Size` de Figma): `md` mide 440 px y `lg`, 520 px. */
export type DialogSize = 'md' | 'lg';

/** Color del mosaico del icono (`Tone` de Figma): `warning` (avisos) o `primary` (una acción principal). */
export type DialogTone = 'warning' | 'primary';

/** Las paradas de Tab de un contenedor y la parada que representa a cada elemento (la de su grupo de radios). */
interface TabStops {
  readonly stops: HTMLElement[];
  readonly stopOf: (element: Element | null) => Element | null | undefined;
}

let nextId = 0;

/** Diálogos abiertos, el último abierto al final: solo ese atrapa el foco y atiende Escape. */
const openDialogs: DialogComponent[] = [];

/**
 * Diálogo modal (Figma: `Dialog`) sobre `bg/overlay`, p. ej. el aviso de sesión por expirar que exige
 * WCAG 2.2.1. `open` lo controla la pantalla; el diálogo no se cierra solo.
 *
 * Contenido proyectado (H2‑11, Figma 73:482): lo que lleva el atributo `livDialogBody` es el **cuerpo**,
 * entre el texto y las acciones y sangrado para alinearse con el texto (un formulario, opciones o avisos);
 * todo lo demás son las **acciones**, en el orden en que se leen (la secundaria primero). Sin cuerpo, `size`
 * ni `tone`, el diálogo es el de siempre: 440 px, mosaico de advertencia y todo el contenido en las acciones.
 *
 * Al abrirse, el foco va al elemento marcado con `data-initial-focus` (o a su primer enfocable) o, si no lo hay,
 * al primer enfocable; el foco queda atrapado adentro y, al cerrarse, vuelve a quien lo tenía. Las paradas de
 * Tab son las del navegador (el detalle y sus límites están en `tabStops`): un grupo de radios cuenta como
 * **una** y no cuentan los controles inhabilitados. La misma regla vale para el primer foco al abrir. Escape
 * emite `closeRequested` si `dismissible` es verdadero; en acciones críticas, pásalo en falso. Hacer clic fuera
 * no cierra, para no perder lo que el usuario estaba haciendo.
 *
 * El diálogo no vuelve inerte el resto de la página: lo atrapa el foco y `aria-modal` lo oculta a los
 * lectores de pantalla. Con varios diálogos abiertos, solo el último abierto atrapa el foco y atiende
 * Escape.
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
  /** Ancho (`Size` de Figma): `md` (440 px, por omisión) o `lg` (520 px). Sin valor u otro valor, `md`. */
  readonly size = input<DialogSize | undefined>('md');
  /** Color del mosaico (`Tone` de Figma): `warning` (por omisión) o `primary`. Sin valor u otro valor, `warning`. */
  readonly tone = input<DialogTone | undefined>('warning');
  /** Escape pidió cerrar: la pantalla decide si lo hace. */
  readonly closeRequested = output<void>();

  private readonly uid = nextId++;
  protected readonly titleId = `liv-dialog-title-${this.uid}`;
  protected readonly descriptionId = `liv-dialog-description-${this.uid}`;
  protected readonly sizeName = computed<DialogSize>(() => (this.size() === 'lg' ? 'lg' : 'md'));
  protected readonly toneName = computed<DialogTone>(() =>
    this.tone() === 'primary' ? 'primary' : 'warning',
  );
  protected readonly iconName = computed<IconName>(() => this.icon() ?? 'history');
  protected readonly descriptionText = computed(() => this.description()?.trim() || null);

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);

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
    const previous = this.document.activeElement;
    openDialogs.push(this);
    const onKeydown = (event: KeyboardEvent) => this.onKeydown(event);
    const onFocusin = (event: FocusEvent) => this.onFocusin(event);
    this.document.addEventListener('keydown', onKeydown);
    this.document.addEventListener('focusin', onFocusin);
    afterNextRender(() => this.focusInitial(), { injector: this.injector });
    return () => {
      const wasTopmost = this.isTopmost();
      // El índice falta si la limpieza corre dos veces: sin la guarda, `splice(-1)` quitaría a otro diálogo.
      const index = openDialogs.indexOf(this);
      if (index >= 0) {
        openDialogs.splice(index, 1);
      }
      this.document.removeEventListener('keydown', onKeydown);
      this.document.removeEventListener('focusin', onFocusin);
      // Si se cierra uno que no era el de arriba, el foco sigue en el de arriba.
      if (wasTopmost && previous instanceof HTMLElement) {
        previous.focus();
      }
    };
  }

  private onKeydown(event: KeyboardEvent) {
    if (!this.isTopmost()) {
      return;
    }
    if (event.key === 'Escape') {
      this.requestClose(event);
    } else if (event.key === 'Tab') {
      this.trapTab(event);
    }
  }

  private isTopmost(): boolean {
    return openDialogs[openDialogs.length - 1] === this;
  }

  /** Escape cierra si se puede descartar y nadie más lo atendió (un control interno o un IME). */
  private requestClose(event: KeyboardEvent) {
    if (this.dismissible() && !event.defaultPrevented && !event.isComposing) {
      event.preventDefault();
      this.closeRequested.emit();
    }
  }

  /** Si el foco llega a algo fuera del diálogo, lo devuelve adentro. */
  private onFocusin(event: FocusEvent) {
    const panel = this.panel()?.nativeElement;
    if (
      this.isTopmost() &&
      panel &&
      event.target instanceof Node &&
      !panel.contains(event.target)
    ) {
      this.focusInitial();
    }
  }

  /**
   * Tab en la última parada salta a la primera y Mayús+Tab en la primera salta a la última. Las paradas son
   * las del navegador (`tabStops`): un grupo de radios es una sola, y cuál de sus radios la representa
   * depende de la dirección. Con el foco en el propio panel (un clic en una zona sin controles), Mayús+Tab
   * también da la vuelta: antes del panel no hay nada de este diálogo.
   */
  private trapTab(event: KeyboardEvent) {
    const panel = this.panel()?.nativeElement;
    if (!panel) {
      return;
    }
    const backwards = event.shiftKey;
    const { stops, stopOf } = this.tabStops(panel, backwards);
    const first = stops[0];
    const last = stops[stops.length - 1];
    const active = this.document.activeElement;
    if (!first || !panel.contains(active)) {
      event.preventDefault();
      (first ?? panel).focus();
    } else if (backwards && (active === panel || stopOf(active) === first)) {
      event.preventDefault();
      last.focus();
    } else if (!backwards && stopOf(active) === last) {
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

  /**
   * El elemento marcado con `data-initial-focus` si es enfocable; si es un contenedor, su primera parada
   * de Tab (con la regla de los grupos de radios, que puede depender de radios de fuera del contenedor);
   * si no hay marca, la primera parada del panel; y si no hay paradas, el panel.
   */
  private initialFocusTarget(panel: HTMLElement): HTMLElement {
    const marked = panel.querySelector<HTMLElement>('[data-initial-focus]');
    if (marked?.matches(FOCUSABLE) && !this.isDisabled(marked)) {
      return marked;
    }
    const stops = this.tabStops(panel, false).stops;
    return (marked && stops.find((stop) => marked.contains(stop))) || stops[0] || panel;
  }

  /**
   * Las paradas de Tab dentro de `root`, en el orden del documento, como las ve el navegador (comprobado en
   * Chromium; Firefox y WebKit no). No cuentan los controles inhabilitados (tampoco los de un `fieldset`
   * inhabilitado, salvo los de su primera `legend`) ni los que llevan `tabindex` negativo. Los radios de un
   * mismo grupo (mismo `name` y mismo formulario) son una sola parada: el marcado o, si no hay ninguno (o el
   * marcado no se puede enfocar), el primer radio enfocable al avanzar y el último al retroceder. Un radio
   * sin `name` no forma grupo. `stopOf` da la parada de un elemento: la de su grupo, si es un radio de un
   * grupo.
   *
   * Límite: en un grupo sin marcar, Chromium vuelve a entrar por el último radio que tuvo el foco; aquí
   * siempre es el primero (al avanzar) o el último (al retroceder), así que «como el navegador» vale con
   * la página recién cargada. No descarta los elementos ocultos (`hidden`, `display: none`, `inert`): la pantalla no debe dejarlos
   * en el diálogo.
   */
  private tabStops(root: HTMLElement, backwards: boolean): TabStops {
    const candidates = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
      (element) => element.tabIndex >= 0 && !this.isDisabled(element),
    );
    const groups = this.radioGroups(root);
    const stopOf = (element: Element | null) => {
      const members = element instanceof HTMLInputElement ? groups.get(element) : undefined;
      return members ? this.radioGroupStop(members, backwards) : element;
    };
    return { stops: candidates.filter((element) => stopOf(element) === element), stopOf };
  }

  /** Cada radio con `name` de `root`, con todos los radios de su grupo (también los inhabilitados). */
  private radioGroups(root: HTMLElement): Map<HTMLInputElement, HTMLInputElement[]> {
    const groups = new Map<HTMLInputElement, HTMLInputElement[]>();
    const byKey = new Map<string, HTMLInputElement[]>();
    const formIds = new Map<HTMLFormElement | null, number>();
    for (const radio of root.querySelectorAll<HTMLInputElement>('input[type="radio"][name]')) {
      if (!radio.name) {
        continue;
      }
      if (!formIds.has(radio.form)) {
        formIds.set(radio.form, formIds.size);
      }
      const key = `${formIds.get(radio.form)}:${radio.name}`;
      const members = byKey.get(key) ?? [];
      members.push(radio);
      byKey.set(key, members);
      groups.set(radio, members);
    }
    return groups;
  }

  /** La parada de un grupo de radios, o `undefined` si ninguno es enfocable. */
  private radioGroupStop(members: HTMLInputElement[], backwards: boolean) {
    const focusable = members.filter((member) => member.tabIndex >= 0 && !this.isDisabled(member));
    // Como Chromium, un radio marcado que no se puede enfocar cuenta como «ninguno marcado».
    const checked = focusable.find((member) => member.checked);
    return checked ?? focusable[backwards ? focusable.length - 1 : 0];
  }

  /** Si un control de formulario está inhabilitado, por sí mismo o por un `fieldset` inhabilitado. */
  private isDisabled(element: HTMLElement): boolean {
    return (
      FORM_CONTROL.test(element.tagName) &&
      (element.hasAttribute('disabled') || this.isInDisabledFieldset(element))
    );
  }

  /**
   * Si el elemento está en un `fieldset` inhabilitado, o en uno anidado en él. Como dice el HTML, los
   * controles de la primera `legend` del `fieldset` no quedan inhabilitados.
   */
  private isInDisabledFieldset(element: HTMLElement): boolean {
    for (
      let fieldset = element.closest('fieldset[disabled]');
      fieldset;
      fieldset = fieldset.parentElement?.closest('fieldset[disabled]') ?? null
    ) {
      const legend = Array.from(fieldset.children).find((child) => child.tagName === 'LEGEND');
      if (!legend?.contains(element)) {
        return true;
      }
    }
    return false;
  }
}
