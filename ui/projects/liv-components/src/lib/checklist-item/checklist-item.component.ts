import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { BadgeComponent, BadgeTone } from '../badge/badge.component';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { isPlainClick, linkTarget } from '../shared/link-click';

/** Estado de un punto: cumplido (`Met`), pendiente (`Pending`) o con observación (`Observed`). */
export type ChecklistItemStatus = 'met' | 'pending' | 'observed';

interface StatusPresentation {
  /** `null`: el círculo vacío, que dibuja el propio componente. */
  readonly icon: IconName | null;
  readonly label: string;
  readonly tone: BadgeTone;
}

const STATUSES: Record<ChecklistItemStatus, StatusPresentation> = {
  met: { icon: 'circle-check', label: 'Cumplido', tone: 'success' },
  pending: { icon: null, label: 'Pendiente', tone: 'neutral' },
  observed: { icon: 'triangle-alert', label: 'Con observación', tone: 'warning' },
};

/**
 * Punto de una lista de verificación, como la lista de preparación de una vigencia (Figma:
 * `ChecklistItem`, nodo 64:573). Es un elemento de lista: se usa como `<li livChecklistItem>` dentro de un
 * `<ol role="list">` (el `role` conserva la lista en los lectores de pantalla aunque se quiten los
 * números del navegador). `Title` de Figma es `heading`, para no chocar con el atributo `title` del
 * navegador.
 *
 * El estado nunca va solo en el icono (decorativo): el `Badge` de la derecha lo dice con texto. `Pending`
 * dibuja un círculo vacío propio porque `Icon` todavía no tiene `circle` (PR 8d); cuando lo tenga, este
 * componente lo usa y el dibujo desaparece.
 *
 * El enlace (`link` y `linkHref`) lleva a la opción donde se resuelve el punto. Como en `NavItem`, un clic
 * normal emite `navigate` con el destino y cancela la navegación del navegador, para que la aplicación
 * navegue con su enrutador; con Ctrl, Cmd, Mayús, Alt o el botón central el navegador lo abre como
 * siempre. Sin texto o sin destino no hay enlace: un enlace sin destino no lleva a ninguna parte.
 */
@Component({
  // Selector de atributo a propósito: el anfitrión es el propio `li` y un elemento `liv-*` entre el `ol` y
  // sus `li` rompería la estructura de la lista (regla `list` de axe).
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'li[livChecklistItem]',
  imports: [BadgeComponent, IconComponent],
  templateUrl: './checklist-item.component.html',
  styleUrl: './checklist-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-status]': 'status()',
  },
})
export class ChecklistItemComponent {
  readonly status = input.required<ChecklistItemStatus>();
  /** Número del punto, como se muestra («1.»). Vacío, no se dibuja. */
  readonly number = input<string>();
  /** Título del punto (`Title` de Figma). */
  readonly heading = input.required<string>();
  /** Por qué falta (`Reason` y `Show reason` de Figma); vacío, no se muestra. */
  readonly reason = input<string>();
  /** Muestra «Opcional» junto al título (`Optional` de Figma). */
  readonly optional = input(false, { transform: booleanAttribute });
  /** Texto del enlace (`Link` y `Show link` de Figma). */
  readonly link = input<string>();
  /** Destino del enlace. */
  readonly linkHref = input<string>();
  /** Clic normal sobre el enlace: la aplicación navega a este destino. */
  readonly navigate = output<string>();

  protected readonly presentation = computed(() => STATUSES[this.status()]);
  protected readonly numberText = computed(() => this.number()?.trim() || null);
  protected readonly reasonText = computed(() => this.reason()?.trim() || null);
  protected readonly linkText = computed(() => this.link()?.trim() || null);
  protected readonly target = computed(() => linkTarget(this.linkHref()));

  protected onLinkClick(event: MouseEvent): void {
    const href = this.target();
    if (href && isPlainClick(event)) {
      event.preventDefault();
      this.navigate.emit(href);
    }
  }
}
