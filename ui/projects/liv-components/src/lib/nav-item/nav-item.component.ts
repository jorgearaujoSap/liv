import { NgTemplateOutlet } from '@angular/common';
import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
  output,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

/**
 * Tipo de opción: `item` (con icono), `group` (módulo con icono y chevron que se expande, y cuyas
 * subopciones se proyectan) o `subitem` (opción de un módulo, alineada con el texto, sin icono).
 */
export type NavItemType = 'item' | 'group' | 'subitem';

/**
 * Opción del menú lateral (Figma: `NavItem`). El texto se proyecta como contenido (`Label` de Figma).
 *
 * `item` y `subitem` son enlaces: con `href`, un clic normal emite `navigate` con el destino y cancela la
 * navegación del navegador, para que la aplicación navegue con su enrutador; con Ctrl, Cmd, Mayús, Alt o
 * el botón central el navegador abre el enlace como siempre. `active` marca la pantalla actual con
 * `aria-current="page"`. `group` es un botón con `aria-expanded`; `active` solo lo pinta (el grupo que
 * contiene la pantalla actual) y no declara `aria-current`.
 *
 * `disabled` es una opción aún no construida: no tiene `href`, no navega ni se expande, declara
 * `aria-disabled="true"`, explica por qué con `disabledHint` y el clic no llega a quien escuche el
 * anfitrión. Hover (fondo `bg-subtle`) y Focus (anillo `focus/ring`) son los del navegador.
 */
@Component({
  selector: 'liv-nav-item',
  imports: [IconComponent, NgTemplateOutlet],
  templateUrl: './nav-item.component.html',
  styleUrl: './nav-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-type]': 'type()',
    '[attr.data-active]': 'active() ? "" : null',
    '[attr.data-disabled]': 'disabled() ? "" : null',
  },
})
export class NavItemComponent {
  readonly type = input<NavItemType>('item');
  /** Icono de 20 px (`Icon` de Figma); un `subitem` no lo dibuja. */
  readonly icon = input<IconName>();
  /** Destino del enlace de un `item` o `subitem`. */
  readonly href = input<string>();
  readonly active = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Si el `group` está expandido (enlazable con `[(expanded)]`). */
  readonly expanded = model(false);
  /** Tooltip de una opción deshabilitada. */
  readonly disabledHint = input('Disponible próximamente');
  /** Clic normal sobre un enlace habilitado: la aplicación navega a este destino. */
  readonly navigate = output<string>();

  protected readonly iconName = computed(() => (this.type() === 'subitem' ? null : this.icon()));

  protected onLinkClick(event: MouseEvent) {
    const href = this.href();
    if (this.disabled()) {
      event.preventDefault();
      event.stopPropagation();
    } else if (href && isPlainClick(event)) {
      event.preventDefault();
      this.navigate.emit(href);
    }
  }

  protected onGroupClick(event: MouseEvent) {
    if (this.disabled()) {
      event.preventDefault();
      event.stopPropagation();
    } else {
      this.expanded.update((expanded) => !expanded);
    }
  }
}

/** Clic principal sin teclas modificadoras: el que la aplicación debe resolver con su enrutador. */
function isPlainClick(event: MouseEvent) {
  return event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey;
}
