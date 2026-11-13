import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { contrastRatio, solidColor } from '../../testing/contrast';
import { cssVariable, declaredStyle, usesToken } from '../../testing/css';
import { BadgeComponent } from '../badge/badge.component';
import { SkeletonComponent } from '../skeleton/skeleton.component';
import { TableCellComponent } from './table-cell.component';
import rowCss from './table-row.component.css' with { loader: 'text' };
import { TableRowComponent } from './table-row.component';
import { TableComponent } from './table.component';

/** Filas de «cargando» y marcadores por fila (Figma 64:446): 3 × 5 = 15. */
const LOADING_ROWS = 3;
const SKELETONS_PER_ROW = 5;

/**
 * Las vigencias de la 1.4 (Figma 64:446): Año (enlace), Inicio, Fin, Estado (Badge), Liquidación y la
 * columna de acción, cuyo encabezado lleva un texto que solo leen los lectores de pantalla.
 */
@Component({
  imports: [
    TableComponent,
    TableRowComponent,
    TableCellComponent,
    BadgeComponent,
    SkeletonComponent,
  ],
  template: `
    <table livTable caption="Vigencias fiscales" [loading]="loading()">
      <thead>
        <tr livTableRow type="header">
          <th livTableCell>Año</th>
          <th livTableCell>Inicio</th>
          <th livTableCell>Fin</th>
          <th livTableCell>Estado</th>
          <th livTableCell>Liquidación</th>
          <th livTableCell type="action">Ver detalle</th>
        </tr>
      </thead>
      <tbody>
        @if (loading()) {
          @for (row of [1, 2, 3]; track row) {
            <tr livTableRow>
              <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
              <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
              <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
              <td livTableCell><liv-skeleton width="88px" height="20px" /></td>
              <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
              <td livTableCell></td>
            </tr>
          }
        } @else {
          @for (row of rows; track row.year) {
            <tr livTableRow>
              <td livTableCell type="link" [href]="row.href" (navigate)="navigated.push($event)">
                {{ row.year }}
              </td>
              <td livTableCell>{{ row.start }}</td>
              <td livTableCell>{{ row.end }}</td>
              <td livTableCell type="badge">
                <liv-badge [tone]="row.tone">{{ row.status }}</liv-badge>
              </td>
              <td livTableCell>No habilitada</td>
              <td livTableCell type="action"></td>
            </tr>
          }
        }
      </tbody>
    </table>
  `,
})
class HostComponent {
  loading = signal(false);
  navigated: string[] = [];
  rows: {
    year: number;
    href?: string;
    start: string;
    end: string;
    status: string;
    tone: 'neutral' | 'primary' | 'success';
  }[] = [
    {
      year: 2027,
      href: '/vigencias/2027',
      start: '1 de enero de 2027',
      end: '31 de diciembre de 2027',
      status: 'Planeada',
      tone: 'neutral',
    },
    {
      year: 2026,
      href: '/vigencias/2026',
      start: '1 de enero de 2026',
      end: '31 de diciembre de 2026',
      status: 'En preparación',
      tone: 'primary',
    },
    {
      year: 2025,
      href: '/vigencias/2025',
      start: '1 de enero de 2025',
      end: '31 de diciembre de 2025',
      status: 'Habilitada',
      tone: 'success',
    },
  ];
}

/** Una fila de ejemplo, para las pruebas que cambian uno de sus campos. */
const SAMPLE_ROW = new HostComponent().rows[0];

interface RenderInputs extends Partial<Omit<HostComponent, 'loading'>> {
  loading?: boolean;
}

function render(inputs: RenderInputs = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  const { loading, ...rest } = inputs;
  Object.assign(fixture.componentInstance, rest);
  if (loading !== undefined) fixture.componentInstance.loading.set(loading);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const table = root.querySelector('table') as HTMLTableElement;
  const rows = Array.from(table.querySelectorAll<HTMLTableRowElement>('tbody tr'));
  const links = Array.from(table.querySelectorAll<HTMLAnchorElement>('a'));
  return { fixture, root, table, rows, links, page: fixture.componentInstance };
}

/** Teclado: `:focus-visible` solo se activa tras una tecla, como en el navegador. */
function focusWithKeyboard(element: HTMLElement): void {
  document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
  element.focus();
}

/**
 * Despacha un clic sobre el enlace y dice si el componente lo canceló. Después lo cancela este oyente
 * (se registra último), para que jsdom no intente navegar.
 */
function click(link: HTMLAnchorElement, init: MouseEventInit = {}): boolean {
  let canceledByComponent = false;
  link.addEventListener(
    'click',
    (event) => {
      canceledByComponent = event.defaultPrevented;
      event.preventDefault();
    },
    { once: true },
  );
  link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, ...init }));
  return canceledByComponent;
}

const color = (name: string) => solidColor(cssVariable(theme, `--color-${name}`));

/** Los elementos que reciben el foco con el Tab. */
const TABBABLE = 'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])';

/** Cuerpo de la regla de `table-row.component.css` con ese selector, en el texto fuente. */
function ruleBody(selector: string): string {
  const start = rowCss.indexOf(`${selector} {`);
  if (start === -1) throw new Error(`La hoja de la fila no tiene la regla ${selector}`);
  return rowCss.slice(start, rowCss.indexOf('}', start));
}

describe('Table', () => {
  it('es una tabla HTML real con su encabezado y su cuerpo', () => {
    const { table } = render();
    expect(table.tagName).toBe('TABLE');
    expect(table.tHead?.rows).toHaveLength(1);
    expect(table.tBodies[0].rows).toHaveLength(3);
    expect(table.rows[0].cells).toHaveLength(6);
  });

  it('su nombre accesible es el caption, que va primero', () => {
    const { table } = render();
    const caption = table.firstElementChild as HTMLTableCaptionElement;
    expect(caption.tagName).toBe('CAPTION');
    expect(caption.textContent?.trim()).toBe('Vigencias fiscales');
    expect(table.caption).toBe(caption);
    expect(table.hasAttribute('aria-label')).toBe(false);
  });

  it('el caption se oculta a la vista pero se conserva para los lectores de pantalla', () => {
    const { table } = render();
    const caption = table.caption as HTMLElement;
    expect(declaredStyle(caption, 'position')).toBe('absolute');
    expect(declaredStyle(caption, 'width')).toBe('1px');
    expect(declaredStyle(caption, 'height')).toBe('1px');
  });

  describe('nombre accesible: caption o aria-label', () => {
    @Component({
      imports: [TableComponent],
      template: `<table livTable [caption]="caption()" [aria-label]="label()">
        <tbody></tbody>
      </table>`,
    })
    class NameHost {
      caption = signal<string | undefined>('Vigencias fiscales');
      label = signal<string | undefined>(undefined);
    }

    @Component({
      imports: [TableComponent],
      template: `<table livTable aria-label="Vigencias fiscales">
        <tbody></tbody>
      </table>`,
    })
    class StaticLabelHost {}

    function mount(caption: string | undefined, label: string | undefined) {
      const fixture = TestBed.createComponent(NameHost);
      fixture.componentInstance.caption.set(caption);
      fixture.componentInstance.label.set(label);
      return fixture;
    }

    it('con solo aria-label, ese es el nombre y no hay <caption>', () => {
      const fixture = mount(undefined, 'Vigencias fiscales');
      fixture.detectChanges();
      const table = (fixture.nativeElement as HTMLElement).querySelector('table') as HTMLElement;
      expect(table.getAttribute('aria-label')).toBe('Vigencias fiscales');
      expect(table.querySelector('caption')).toBeNull();
    });

    it('el aria-label también puede ir como atributo estático', () => {
      const fixture = TestBed.createComponent(StaticLabelHost);
      fixture.detectChanges();
      const table = (fixture.nativeElement as HTMLElement).querySelector('table') as HTMLElement;
      expect(table.getAttribute('aria-label')).toBe('Vigencias fiscales');
    });

    it('con los dos, el aria-label manda y el caption se conserva', () => {
      const fixture = mount('Vigencias', 'Vigencias fiscales de Ipiales');
      fixture.detectChanges();
      const table = (fixture.nativeElement as HTMLElement).querySelector('table') as HTMLElement;
      expect(table.getAttribute('aria-label')).toBe('Vigencias fiscales de Ipiales');
      expect(table.querySelector('caption')?.textContent?.trim()).toBe('Vigencias');
    });

    it('con uno de los dos basta: el otro puede ir vacío', () => {
      for (const [caption, label] of [
        ['Vigencias fiscales', ''],
        ['  ', 'Vigencias fiscales'],
        [undefined, 'Vigencias fiscales'],
        ['Vigencias fiscales', undefined],
      ] as const) {
        expect(() => mount(caption, label).detectChanges()).not.toThrow();
      }
    });

    it('sin ninguno, o con los dos vacíos, lanza un error en desarrollo', () => {
      for (const [caption, label] of [
        [undefined, undefined],
        ['', ''],
        ['   ', undefined],
        [undefined, '   '],
      ] as const) {
        expect(() => mount(caption, label).detectChanges()).toThrow(/caption|aria-label/i);
      }
    });

    it('también si se quedan sin nombre con la tabla ya montada', () => {
      const fixture = mount('Vigencias fiscales', undefined);
      fixture.detectChanges();
      fixture.componentInstance.caption.set('');
      expect(() => fixture.detectChanges()).toThrow(/caption|aria-label/i);
    });

    it('cumple axe (007-21) con aria-label en lugar de caption', async () => {
      const fixture = mount(undefined, 'Vigencias fiscales');
      fixture.detectChanges();
      await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
    });
  });

  it('Default: sin aria-busy', () => {
    expect(render().table.hasAttribute('aria-busy')).toBe(false);
  });

  it('Loading: aria-busy="true" y las filas cargando no tienen enlaces', () => {
    const { table, links, page, fixture } = render({ loading: true });
    expect(table.getAttribute('aria-busy')).toBe('true');
    expect(links).toHaveLength(0);
    expect(table.querySelectorAll('liv-skeleton')).toHaveLength(LOADING_ROWS * SKELETONS_PER_ROW);

    page.loading.set(false);
    fixture.detectChanges();
    expect(table.hasAttribute('aria-busy')).toBe(false);
    expect(table.querySelectorAll('liv-skeleton')).toHaveLength(0);
  });

  it('pinta la superficie del diseño: fondo bg/surface, borde border/default de 1 px y radius/xl', () => {
    const { table } = render();
    expect(usesToken(getComputedStyle(table).backgroundColor, '--color-bg-surface')).toBe(true);
    expect(declaredStyle(table, 'border-width')).toBe('1px');
    expect(declaredStyle(table, 'border-color')).toContain('var(--color-border-default,');
    expect(declaredStyle(table, 'border-radius')).toContain('var(--radius-xl,');
    expect(declaredStyle(table, 'overflow')).toBe('hidden');
  });

  it('reparte las columnas por igual: layout fijo y a todo el ancho', () => {
    const { table } = render();
    expect(declaredStyle(table, 'table-layout')).toBe('fixed');
    expect(declaredStyle(table, 'width')).toBe('100%');
    expect(declaredStyle(table, 'border-collapse')).toBe('separate');
  });

  describe('axe (007-21)', () => {
    it.each<[string, RenderInputs]>([
      ['con datos', {}],
      ['cargando', { loading: true }],
      ['sin filas', { rows: [] }],
      ['con una fila sin destino', { rows: [{ ...SAMPLE_ROW, href: undefined }] }],
      [
        'con un texto largo que ocupa varias líneas',
        { rows: [{ ...SAMPLE_ROW, status: 'x '.repeat(80) }] },
      ],
    ])('cumple axe %s', async (_name, inputs) => {
      await expectNoAxeViolations(render(inputs).root);
    });

    it('cumple axe con el foco en el enlace de una fila', async () => {
      const { root, links } = render();
      focusWithKeyboard(links[1]);
      await expectNoAxeViolations(root);
    });
  });
});

describe('TableRow', () => {
  it('Header: fondo bg/subtle', () => {
    const { table } = render();
    const header = table.tHead?.rows[0] as HTMLElement;
    expect(header.dataset['type']).toBe('header');
    expect(usesToken(getComputedStyle(header).backgroundColor, '--color-bg-subtle')).toBe(true);
  });

  it('una fila de <thead> sin type es la del encabezado; una de <tbody>, de datos', () => {
    @Component({
      imports: [TableComponent, TableRowComponent, TableCellComponent],
      template: `
        <table livTable caption="Vigencias fiscales">
          <thead>
            <tr livTableRow>
              <th livTableCell>Año</th>
            </tr>
          </thead>
          <tbody>
            <tr livTableRow>
              <td livTableCell>2026</td>
            </tr>
          </tbody>
        </table>
      `,
    })
    class Inferred {}
    const fixture = TestBed.createComponent(Inferred);
    fixture.detectChanges();
    const [head, body] = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('tr'),
    );
    expect(head.dataset['type']).toBe('header');
    expect(body.dataset['type']).toBe('body');
  });

  it('Body es el tipo por omisión: fondo bg/surface', () => {
    const { rows } = render();
    expect(rows.every((row) => row.dataset['type'] === 'body')).toBe(true);
    expect(usesToken(getComputedStyle(rows[0]).backgroundColor, '--color-bg-surface')).toBe(true);
  });

  it('Hover (Body): fondo bg/subtle; la fila del encabezado no cambia', () => {
    // jsdom no evalúa :hover: se comprueba el texto de la regla (la única que lo usa) y se revisa en Storybook.
    expect(rowCss.match(/:hover/g)).toHaveLength(1);
    expect(ruleBody(":host([data-type='body']:hover)")).toContain('bg-bg-subtle');
  });

  it('Focus: borde border/focus de 2 px dentro de la fila, solo mientras el enlace tiene el foco del teclado', () => {
    const { rows, links } = render();
    expect(declaredStyle(rows[1], 'outline-color')).toBe('');
    focusWithKeyboard(links[1]);
    expect(rows[1].matches(':has(:focus-visible)')).toBe(true);
    expect(declaredStyle(rows[1], 'outline-width')).toBe('2px');
    expect(declaredStyle(rows[1], 'outline-offset')).toContain('-2px');
    expect(declaredStyle(rows[1], 'outline-color')).toContain('var(--color-border-focus');
    // Solo la fila del enlace enfocado.
    expect(declaredStyle(rows[0], 'outline-color')).toBe('');
    links[1].blur();
    expect(declaredStyle(rows[1], 'outline-color')).toBe('');
  });

  it('con el ratón no pinta el contorno: el foco de la fila es solo del teclado', () => {
    // Un clic enfoca el enlace, pero `:focus-visible` no se activa: la regla lo exige, no `:focus-within`.
    expect(rowCss).not.toContain(':focus-within');
    expect(rowCss).toContain(':has(:focus-visible)');
    const { rows, links } = render();
    links[1].dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    links[1].focus();
    expect(declaredStyle(rows[1], 'outline-color')).toBe('');
  });

  it('el foco solo existe en las filas de datos: la regla exige data-type="body"', () => {
    // La fila del encabezado no tiene nada enfocable, así que se comprueba el texto de la regla.
    expect(ruleBody(":host([data-type='body']:has(:focus-visible))")).toContain(
      'outline-border-focus',
    );
  });

  it('solo el enlace de cada fila recibe el foco: ni las celdas ni el chevron', () => {
    const { rows } = render();
    for (const row of rows) {
      expect(row.querySelectorAll(TABBABLE)).toHaveLength(1);
      expect(row.querySelector(TABBABLE)?.tagName).toBe('A');
    }
  });

  it('el foco es un contorno de 2 px y no una sombra, así que sigue visible en alto contraste', () => {
    const { rows, links } = render();
    focusWithKeyboard(links[0]);
    expect(declaredStyle(rows[0], 'outline-width')).toBe('2px');
    expect(declaredStyle(rows[0], 'outline-style')).not.toMatch(/^(|none)$/);
    expect(declaredStyle(rows[0], 'box-shadow')).toBe('');
    // El enlace no dibuja otro anillo: el indicador de foco de la fila es el único.
    expect(declaredStyle(links[0], 'outline-style')).toBe('none');
  });

  it('las filas cargando no tienen nada enfocable', () => {
    const { rows } = render({ loading: true });
    expect(rows).toHaveLength(LOADING_ROWS);
    expect(rows.every((row) => row.querySelectorAll(TABBABLE).length === 0)).toBe(true);
  });
});

describe('TableCell', () => {
  describe('Header', () => {
    it('es un th con scope="col"', () => {
      const { table } = render();
      const headers = Array.from(table.querySelectorAll<HTMLElement>('th'));
      expect(headers).toHaveLength(6);
      expect(headers.every((header) => header.getAttribute('scope') === 'col')).toBe(true);
    });

    it('un td no lleva scope', () => {
      const { table } = render();
      expect(table.querySelector('td')?.hasAttribute('scope')).toBe(false);
    });

    it('label/sm en text/secondary y 40 px de alto', () => {
      const { table } = render();
      const header = table.querySelector('th') as HTMLElement;
      expect(usesToken(getComputedStyle(header).color, '--color-text-secondary')).toBe(true);
      expect(declaredStyle(header, 'font-size')).toContain('var(--text-label-sm');
      expect(declaredStyle(header, 'height')).toContain('var(--spacing-10');
      expect(declaredStyle(header, 'text-align')).toBe('left');
    });

    it('lleva el borde inferior border/default del diseño', () => {
      const { table } = render();
      const header = table.querySelector('th') as HTMLElement;
      expect(declaredStyle(header, 'border-bottom-width')).toBe('1px');
      expect(declaredStyle(header, 'border-color')).toContain('var(--color-border-default,');
    });

    it('cada columna tiene su encabezado, también la de acción', () => {
      const { table } = render();
      const names = Array.from(table.querySelectorAll('th')).map((th) => th.textContent?.trim());
      expect(names).toEqual(['Año', 'Inicio', 'Fin', 'Estado', 'Liquidación', 'Ver detalle']);
    });
  });

  describe('Text', () => {
    it('es un td por omisión: body/md en text/primary y 52 px de alto', () => {
      const { rows } = render();
      const cell = rows[0].cells[1];
      expect(cell.tagName).toBe('TD');
      expect(cell.dataset['type']).toBe('text');
      expect(usesToken(getComputedStyle(cell).color, '--color-text-primary')).toBe(true);
      expect(declaredStyle(cell, 'font-size')).toContain('var(--text-body-md');
      expect(declaredStyle(cell, 'height')).toBe('52px');
      expect(declaredStyle(cell, 'padding-inline')).toContain('var(--spacing-4,');
      expect(cell.textContent?.trim()).toBe('1 de enero de 2027');
    });

    it('un texto largo se parte en varias líneas en vez de salirse de su columna', () => {
      const { rows } = render();
      expect(declaredStyle(rows[0].cells[1], 'overflow-wrap')).toBe('break-word');
    });

    it('lleva el borde inferior border/default del diseño', () => {
      const { rows } = render();
      expect(declaredStyle(rows[0].cells[1], 'border-bottom-width')).toBe('1px');
      expect(declaredStyle(rows[0].cells[1], 'border-color')).toContain(
        'var(--color-border-default,',
      );
    });
  });

  describe('Link', () => {
    it('dibuja su propio <a href> con el texto de la celda', () => {
      const { links } = render();
      expect(links).toHaveLength(3);
      expect(links.map((link) => link.getAttribute('href'))).toEqual([
        '/vigencias/2027',
        '/vigencias/2026',
        '/vigencias/2025',
      ]);
      expect(links[1].textContent?.trim()).toBe('2026');
    });

    it('el enlace es el hijo de la celda: la celda no lleva otro enlace ni el rol', () => {
      const { rows } = render();
      const cell = rows[0].cells[0];
      expect(cell.dataset['type']).toBe('link');
      expect(cell.querySelectorAll('a')).toHaveLength(1);
      expect(cell.hasAttribute('role')).toBe(false);
    });

    it('body/md-strong en text/link, sin subrayado', () => {
      const { links } = render();
      expect(usesToken(getComputedStyle(links[0]).color, '--color-text-link')).toBe(true);
      expect(declaredStyle(links[0], 'font-size')).toContain('var(--text-body-md-strong');
      expect(declaredStyle(links[0], 'text-decoration-line')).toBe('none');
    });

    it('un clic normal emite navigate con el destino y cancela la navegación', () => {
      const { fixture, links, page } = render();
      expect(click(links[1], { button: 0 })).toBe(true);
      fixture.detectChanges();
      expect(page.navigated).toEqual(['/vigencias/2026']);
    });

    it.each(['ctrlKey', 'metaKey', 'shiftKey', 'altKey'] as const)(
      'con %s el navegador abre el enlace como siempre',
      (modifier) => {
        const { links, page } = render();
        expect(click(links[0], { [modifier]: true })).toBe(false);
        expect(page.navigated).toEqual([]);
      },
    );

    it('el botón central tampoco lo resuelve la aplicación', () => {
      const { links, page } = render();
      expect(click(links[0], { button: 1 })).toBe(false);
      expect(page.navigated).toEqual([]);
    });

    it('emite el destino sin espacios sobrantes', () => {
      const { links, page, fixture } = render({
        rows: [{ ...SAMPLE_ROW, href: '  /vigencias/2027  ' }],
      });
      click(links[0]);
      fixture.detectChanges();
      expect(page.navigated).toEqual(['/vigencias/2027']);
      expect(links[0].getAttribute('href')).toBe('/vigencias/2027');
    });

    it('sin destino (o vacío) queda inerte: sin href, sin foco y sin emitir', () => {
      for (const href of [undefined, '', '   ']) {
        const { rows, page } = render({
          rows: [{ ...SAMPLE_ROW, href }],
        });
        const anchor = rows[0].querySelector('a') as HTMLAnchorElement;
        expect(anchor.hasAttribute('href')).toBe(false);
        expect(rows[0].querySelectorAll(TABBABLE)).toHaveLength(0);
        expect(click(anchor)).toBe(false);
        expect(page.navigated).toEqual([]);
      }
    });

    it('sin destino no se pinta como enlace: text/primary en lugar de text/link', () => {
      const { links } = render({ rows: [{ ...SAMPLE_ROW, href: undefined }] });
      expect(usesToken(getComputedStyle(links[0]).color, '--color-text-primary')).toBe(true);
    });
  });

  describe('Badge', () => {
    it('contiene el Badge, cuyo texto dice el estado', () => {
      const { rows } = render();
      const cell = rows[1].cells[3];
      expect(cell.dataset['type']).toBe('badge');
      expect(cell.querySelector('liv-badge')?.textContent?.trim()).toBe('En preparación');
    });
  });

  describe('Action', () => {
    it('dibuja un chevron decorativo de 20 px, oculto a los lectores de pantalla', () => {
      const { rows } = render();
      const cell = rows[0].cells[5];
      expect(cell.dataset['type']).toBe('action');
      const icon = cell.querySelector('liv-icon') as HTMLElement;
      expect(icon).not.toBeNull();
      const svg = icon.querySelector('svg') as SVGElement;
      expect(svg.getAttribute('aria-hidden')).toBe('true');
      expect(svg.getAttribute('width')).toBe('20');
      expect(svg.getAttribute('d') ?? svg.querySelector('path')?.getAttribute('d')).toBe(
        'm9 18 6-6-6-6',
      );
      expect(cell.textContent?.trim()).toBe('');
      expect(cell.querySelectorAll(TABBABLE)).toHaveLength(0);
    });

    it('chevron en text/muted, a la derecha y de 56 px de ancho', () => {
      const { rows } = render();
      const cell = rows[0].cells[5];
      expect(declaredStyle(cell, 'width')).toBe('56px');
      expect(declaredStyle(cell, 'text-align')).toBe('right');
      const chevron = cell.querySelector('.table-cell__chevron') as HTMLElement;
      expect(usesToken(getComputedStyle(chevron).color, '--color-text-muted')).toBe(true);
      // Una caja flex a la derecha: el chevron queda centrado en la fila, no sobre la línea base del texto.
      expect(declaredStyle(chevron, 'display')).toBe('flex');
      expect(declaredStyle(chevron, 'justify-content')).toBe('flex-end');
    });

    it('el encabezado de la columna de acción (th) fija el mismo ancho y no dibuja chevron', () => {
      const { table } = render();
      const header = table.tHead?.rows[0].cells[5] as HTMLElement;
      expect(header.tagName).toBe('TH');
      expect(header.getAttribute('scope')).toBe('col');
      expect(declaredStyle(header, 'width')).toBe('56px');
      expect(header.querySelector('liv-icon')).toBeNull();
    });

    it('el texto del encabezado de acción solo lo leen los lectores de pantalla', () => {
      const { table } = render();
      const header = table.tHead?.rows[0].cells[5] as HTMLElement;
      const label = header.querySelector('.table-cell__label') as HTMLElement;
      expect(label.textContent?.trim()).toBe('Ver detalle');
      expect(declaredStyle(label, 'position')).toBe('absolute');
      expect(declaredStyle(label, 'width')).toBe('1px');
    });
  });
});

describe('TableCell · combinaciones sin sentido', () => {
  @Component({
    imports: [TableComponent, TableRowComponent, TableCellComponent],
    template: `
      <table livTable caption="Combinaciones">
        <thead>
          <tr livTableRow>
            <th livTableCell type="link" href="/x">Enlace en un th</th>
            <th livTableCell type="badge">Badge en un th</th>
          </tr>
        </thead>
        <tbody>
          <tr livTableRow>
            <td livTableCell type="header">Encabezado en un td</td>
            <td livTableCell type="action">contenido que no se muestra</td>
          </tr>
        </tbody>
      </table>
    `,
  })
  class Odd {}

  function renderOdd() {
    const fixture = TestBed.createComponent(Odd);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('un th es siempre un encabezado de columna: ignora link y badge, y no dibuja enlace', () => {
    const root = renderOdd();
    const headers = Array.from(root.querySelectorAll<HTMLElement>('th'));
    expect(headers.map((header) => header.dataset['type'])).toEqual(['header', 'header']);
    expect(root.querySelector('a')).toBeNull();
  });

  it('un td nunca es un encabezado: type="header" cuenta como texto', () => {
    const cells = Array.from(renderOdd().querySelectorAll<HTMLElement>('td'));
    expect(cells[0].dataset['type']).toBe('text');
    expect(cells[0].hasAttribute('scope')).toBe(false);
  });

  it('la celda de acción no proyecta contenido: solo dibuja el chevron', () => {
    const cell = renderOdd().querySelectorAll<HTMLElement>('td')[1];
    expect(cell.textContent).not.toContain('contenido');
    expect(cell.querySelector('liv-icon')).not.toBeNull();
  });
});

describe('Table · contraste', () => {
  it('textos a 4,5:1 (WCAG 1.4.3) y chevron y foco a 3:1 (1.4.11), sobre superficie y fondo sutil', () => {
    const text: [string, string][] = [
      ['text-primary', 'bg-surface'],
      ['text-primary', 'bg-subtle'],
      ['text-link', 'bg-surface'],
      ['text-link', 'bg-subtle'],
      ['text-secondary', 'bg-subtle'],
    ];
    const nonText: [string, string][] = [
      ['text-muted', 'bg-surface'],
      ['text-muted', 'bg-subtle'],
      ['border-focus', 'bg-surface'],
      ['border-focus', 'bg-subtle'],
    ];
    for (const [foreground, background] of text) {
      expect(contrastRatio(color(foreground), color(background))).toBeGreaterThanOrEqual(4.5);
    }
    for (const [foreground, background] of nonText) {
      expect(contrastRatio(color(foreground), color(background))).toBeGreaterThanOrEqual(3);
    }
  });
});
