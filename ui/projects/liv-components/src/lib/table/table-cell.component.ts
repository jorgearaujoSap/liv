import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { isPlainClick, linkTarget } from '../shared/link-click';

/**
 * Tipo de celda:
 * - `header`: encabezado de columna (un `th` con `scope="col"`);
 * - `text`: texto (por omisión);
 * - `link`: el enlace al detalle de la fila, en la primera columna;
 * - `badge`: un `liv-badge` con el estado;
 * - `action`: la columna de acción al final de la fila, con un chevron decorativo.
 */
export type TableCellType = 'header' | 'text' | 'link' | 'badge' | 'action';

/**
 * Celda de una `<table livTable>` (Figma: `TableCell`, nodo 63:283), sobre un `<th livTableCell>` o un
 * `<td livTableCell>`. El contenido se proyecta.
 *
 * Un `th` es siempre un encabezado de columna (`scope="col"`): no hay encabezado de fila, y `link` o
 * `badge` en un `th` no valen. Con `type="action"` es el de la columna de acción, que fija sus 56 px; su
 * texto es obligatorio (p. ej. «Ver detalle») y solo lo leen los lectores de pantalla, porque sin él el
 * encabezado queda vacío. Un `td` nunca es un encabezado: `type="header"` cuenta como `text`.
 *
 * `link` dibuja su propio `<a href>`, como `NavItem`: un clic normal emite `navigate` con el destino (sin
 * espacios sobrantes, pero sin sanear) y cancela la navegación del navegador, para que la aplicación
 * navegue con su enrutador; con Ctrl, Cmd, Mayús, Alt o el botón central el navegador abre el enlace
 * como siempre. **La pantalla valida el destino**: solo rutas propias, nunca un `href` que venga de
 * un dato externo (`javascript:`, `https://…`, `//host`). Sin `href`, o con uno vacío, el texto queda
 * inerte y se pinta como texto. Es el único elemento de la fila que recibe el foco.
 *
 * `action` dibuja el chevron (decorativo, `aria-hidden`) y no proyecta contenido.
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- tabla HTML real: se aplica al <th> y al <td> nativos
  selector: 'th[livTableCell], td[livTableCell]',
  imports: [IconComponent, NgTemplateOutlet],
  templateUrl: './table-cell.component.html',
  styleUrl: './table-cell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-type]': 'kind()',
    '[attr.data-header]': 'isHeader ? "" : null',
    '[attr.scope]': 'isHeader ? "col" : null',
  },
})
export class TableCellComponent {
  /** `Type` de Figma. En un `th` solo cuenta `action`; en un `td`, todos menos `header`. */
  readonly type = input<TableCellType>();
  /** Destino del enlace de un `link`. */
  readonly href = input<string>();
  /** Clic normal sobre el enlace: la aplicación navega a este destino. */
  readonly navigate = output<string>();

  protected readonly isHeader =
    inject<ElementRef<HTMLElement>>(ElementRef).nativeElement.tagName === 'TH';
  protected readonly target = computed(() => linkTarget(this.href()));
  protected readonly kind = computed<TableCellType>(() => {
    const type = this.type();
    if (this.isHeader) return type === 'action' ? 'action' : 'header';
    return !type || type === 'header' ? 'text' : type;
  });

  protected onLinkClick(event: MouseEvent): void {
    const href = this.target();
    if (href && isPlainClick(event)) {
      event.preventDefault();
      this.navigate.emit(href);
    }
  }
}
