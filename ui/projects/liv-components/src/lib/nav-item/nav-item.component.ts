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
import { isPlainClick, linkTarget } from '../shared/link-click';

let nextId = 0;

/**
 * Tipo de opción:
 * - `item` (con icono);
 * - `group` (módulo con icono y chevron que se expande, y cuyas subopciones se proyectan);
 * - `subgroup` (opción de segundo nivel que agrupa opciones de tercer nivel, como 1.5 y 1.6: sin icono,
 *   alineada con el texto del módulo y con chevron; se expande como un `group`);
 * - `subitem` (opción de un módulo, alineada con el texto, sin icono);
 * - `nested` (opción de tercer nivel, 1.5.x y 1.6.x: como un `subitem`, sangrada 16 px más).
 *
 * Anidación válida: `group` contiene `subitem` y `subgroup`; `subgroup` contiene `nested`. Los hijos son
 * `liv-nav-item` directos (no se envuelven en otro elemento) y la sangría es fija por tipo, no por
 * profundidad: otra anidación se ve plana. No cambies `type` con la opción ya creada.
 */
export type NavItemType = 'item' | 'group' | 'subgroup' | 'subitem' | 'nested';

/** Los tipos que se expanden para mostrar las opciones que contienen. */
const BRANCH_TYPES: readonly NavItemType[] = ['group', 'subgroup'];
/**
 * Los tipos que dibujan icono. `subgroup`, `subitem` y `nested` no: se alinean con el texto del módulo
 * (`nested`, 16 px más sangrado).
 */
const ICON_TYPES: readonly NavItemType[] = ['item', 'group'];

/**
 * Opción del menú lateral (Figma: `NavItem`). El texto se proyecta como contenido (`Label` de Figma).
 *
 * `item`, `subitem` y `nested` son enlaces: con `href`, un clic normal emite `navigate` con el destino (sin espacios
 * sobrantes, pero sin sanear) y cancela la navegación del navegador, para que la aplicación navegue con
 * su enrutador y solo a rutas propias; con Ctrl, Cmd, Mayús, Alt o el botón central el navegador abre el
 * enlace como siempre. Un `href` ausente o vacío deja la opción inerte: una opción habilitada necesita
 * destino.
 *
 * `active` marca la pantalla actual con `aria-current="page"`. `group` y `subgroup` son botones con
 * `aria-expanded`; en ellos, `active` solo los pinta (el grupo que contiene la pantalla actual) y no
 * declara `aria-current`: el `group` con el fondo de la pantalla actual y el `subgroup` solo con el color
 * y el peso del texto (`body/md-strong` en `action-primary`), sin relleno, para no apilar tres barras
 * (módulo, grupo y opción actual). Si la rama activa está contraída, la opción actual no está en el DOM:
 * la aplicación debe expandirla (`[(expanded)]`, para que el usuario también pueda contraerla) cuando la
 * pantalla actual esté dentro.
 *
 * `disabled` es una opción aún no construida: no tiene `href`, no navega ni se expande, declara
 * `aria-disabled="true"`, explica por qué con `disabledHint` y el clic no llega a quien escuche el
 * anfitrión. Un grupo que se deshabilita conserva `expanded`: vuelve a abrirse al habilitarse.
 *
 * Hover (fondo `bg-subtle`) y Focus (anillo `focus/ring`) son los del navegador.
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
  /** Icono de 20 px (`Icon` de Figma); solo lo dibujan `item` y `group`. */
  readonly icon = input<IconName>();
  /** Destino del enlace de un `item`, `subitem` o `nested`. */
  readonly href = input<string>();
  readonly active = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Si el `group` o `subgroup` está expandido (enlazable con `[(expanded)]`). */
  readonly expanded = model(false);
  /** Tooltip de una opción deshabilitada. */
  readonly disabledHint = input('Disponible próximamente');
  /** Clic normal sobre un enlace habilitado: la aplicación navega a este destino. */
  readonly navigate = output<string>();

  private readonly uid = nextId++;
  protected readonly controlId = `liv-nav-item-${this.uid}`;
  protected readonly childrenId = `liv-nav-item-children-${this.uid}`;
  protected readonly target = computed(() => linkTarget(this.href()));
  protected readonly isBranch = computed(() => BRANCH_TYPES.includes(this.type()));
  protected readonly iconName = computed(() =>
    ICON_TYPES.includes(this.type()) ? this.icon() : null,
  );

  protected onLinkClick(event: MouseEvent) {
    const href = this.target();
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
