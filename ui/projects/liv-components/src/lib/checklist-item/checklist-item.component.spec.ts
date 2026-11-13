import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { contrastRatio, solidColor } from '../../testing/contrast';
import { cssVariable, declaredStyle, paintsShadow, usesToken } from '../../testing/css';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { ChecklistItemComponent, ChecklistItemStatus } from './checklist-item.component';

@Component({
  imports: [ChecklistItemComponent],
  template: `
    <ol role="list">
      <li
        livChecklistItem
        [status]="status"
        [number]="number"
        [heading]="heading"
        [reason]="reason"
        [optional]="optional"
        [link]="link"
        [linkHref]="linkHref"
        (navigate)="navigated.push($event)"
      ></li>
    </ol>
  `,
})
class HostComponent {
  status: ChecklistItemStatus = 'met';
  number: string | undefined = '1.';
  heading = 'Vigencia en preparación';
  reason: string | undefined = 'La vigencia aún no está en preparación.';
  optional = false;
  link: string | undefined;
  linkHref: string | undefined;
  navigated: string[] = [];
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const list = fixture.nativeElement.querySelector('ol') as HTMLOListElement;
  const item = list.querySelector('li') as HTMLLIElement;
  const part = <T extends Element = HTMLElement>(selector: string) =>
    item.querySelector<T>(selector);
  return { fixture, page: fixture.componentInstance, list, item, part };
}

/** El HTML interior del icono que dibuja `liv-icon` con ese nombre, para saber qué icono es. */
function iconMarkup(name: IconName): string {
  @Component({ imports: [IconComponent], template: `<liv-icon [name]="name" [size]="20" />` })
  class IconHost {
    name = name;
  }
  const fixture = TestBed.createComponent(IconHost);
  fixture.detectChanges();
  return (fixture.nativeElement.querySelector('svg') as SVGElement).innerHTML;
}

const color = (name: string) => solidColor(cssVariable(theme, `--color-${name}`));

/** Teclado: `:focus-visible` solo se activa tras una tecla, como en el navegador. */
function focusWithKeyboard(element: HTMLElement): void {
  document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
  element.focus();
}

const STATUSES: readonly ChecklistItemStatus[] = ['met', 'pending', 'observed'];

describe('ChecklistItem', () => {
  it('es un elemento de lista (li) de una lista ordenada', () => {
    const { list, item } = render();
    expect(item.tagName).toBe('LI');
    expect(item.parentElement).toBe(list);
    expect(list.tagName).toBe('OL');
  });

  it('muestra el número, el título y el motivo', () => {
    const { part } = render();
    expect(part('.checklist-item__number')?.textContent?.trim()).toBe('1.');
    expect(part('.checklist-item__title')?.textContent?.trim()).toBe('Vigencia en preparación');
    expect(part('.checklist-item__reason')?.textContent?.trim()).toBe(
      'La vigencia aún no está en preparación.',
    );
  });

  it('sin número no dibuja su espacio', () => {
    expect(render({ number: undefined }).part('.checklist-item__number')).toBeNull();
    expect(render({ number: '  ' }).part('.checklist-item__number')).toBeNull();
  });

  describe('Status', () => {
    it.each<[ChecklistItemStatus, string, string]>([
      ['met', 'Cumplido', 'success'],
      ['pending', 'Pendiente', 'neutral'],
      ['observed', 'Con observación', 'warning'],
    ])('%s: el Badge dice «%s» con el tono %s', (status, label, tone) => {
      const { item, part } = render({ status });
      expect(item.dataset['status']).toBe(status);
      const badge = part('.checklist-item__status') as HTMLElement;
      expect(badge.tagName).toBe('LIV-BADGE');
      expect(badge.textContent?.trim()).toBe(label);
      expect(badge.dataset['tone']).toBe(tone);
    });

    it('el estado nunca va solo en el icono: el texto del Badge lo dice y el icono es decorativo', () => {
      for (const status of STATUSES) {
        const { part } = render({ status });
        const icon = part('.checklist-item__icon') as HTMLElement;
        expect(icon.getAttribute('aria-hidden')).toBe('true');
        expect(icon.querySelector('[role="img"], [aria-label]')).toBeNull();
        expect(part('.checklist-item__status')?.textContent?.trim()).not.toBe('');
      }
    });

    it('Met usa circle-check y Observed usa triangle-alert, ambos de 20 px', () => {
      expect(render({ status: 'met' }).part('svg')?.innerHTML).toBe(iconMarkup('circle-check'));
      expect(render({ status: 'observed' }).part('svg')?.innerHTML).toBe(
        iconMarkup('triangle-alert'),
      );
      for (const status of ['met', 'observed'] as const) {
        const svg = render({ status }).part('.checklist-item__icon svg') as SVGElement;
        expect(svg.getAttribute('width')).toBe('20');
        expect(svg.getAttribute('height')).toBe('20');
      }
    });

    it('Pending dibuja el círculo vacío de 20 px, sin trazos de otro icono', () => {
      const svg = render({ status: 'pending' }).part('.checklist-item__icon svg') as SVGElement;
      expect(svg.getAttribute('width')).toBe('20');
      expect(svg.getAttribute('height')).toBe('20');
      expect(svg.querySelectorAll('circle')).toHaveLength(1);
      expect(svg.querySelector('circle')?.getAttribute('r')).toBe('10');
      expect(svg.querySelectorAll('path')).toHaveLength(0);
    });

    it('un estado que no es uno de los tres se muestra como pendiente, sin romper la lista', () => {
      const { item, part } = render({ status: 'desconocido' as unknown as ChecklistItemStatus });
      expect(part('.checklist-item__status')?.textContent?.trim()).toBe('Pendiente');
      expect(part('.checklist-item__status')?.dataset['tone']).toBe('neutral');
      expect(item.querySelector('.checklist-item__icon svg circle')).not.toBeNull();
    });

    it('cambia de estado al reasignarlo', () => {
      const { fixture, page, item, part } = render({ status: 'pending' });
      page.status = 'met';
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(item.dataset['status']).toBe('met');
      expect(part('.checklist-item__status')?.textContent?.trim()).toBe('Cumplido');
    });
  });

  it('actualiza el motivo, el enlace y el destino al reasignarlos', () => {
    const { fixture, page, part } = render({ link: 'Ir a', linkHref: '/uno' });
    page.reason = 'Otro motivo';
    page.linkHref = '/dos';
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(part('.checklist-item__reason')?.textContent?.trim()).toBe('Otro motivo');
    expect(part('.checklist-item__link')?.getAttribute('href')).toBe('/dos');
    page.reason = undefined;
    page.link = undefined;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(part('.checklist-item__reason')).toBeNull();
    expect(part('.checklist-item__link')).toBeNull();
  });

  describe('Reason', () => {
    it('sin motivo, o con uno vacío, no se muestra', () => {
      expect(render({ reason: undefined }).part('.checklist-item__reason')).toBeNull();
      expect(render({ reason: '' }).part('.checklist-item__reason')).toBeNull();
      expect(render({ reason: '   ' }).part('.checklist-item__reason')).toBeNull();
    });
  });

  describe('Optional', () => {
    it('Optional muestra «Opcional» como un Badge neutro junto al título', () => {
      const { part } = render({ optional: true });
      const badge = part('.checklist-item__optional') as HTMLElement;
      expect(badge.tagName).toBe('LIV-BADGE');
      expect(badge.textContent?.trim()).toBe('Opcional');
      expect(badge.dataset['tone']).toBe('neutral');
      expect(part('.checklist-item__heading')?.contains(badge)).toBe(true);
    });

    it('por omisión no es opcional', () => {
      expect(render().part('.checklist-item__optional')).toBeNull();
    });
  });

  describe('Link', () => {
    const LINK = { link: 'Ir a 1.5.2 · Catálogos de avalúos', linkHref: '/avaluos/catalogos' };

    it('Show link dibuja un enlace <a href> con su texto, debajo del motivo', () => {
      const { part } = render(LINK);
      const link = part<HTMLAnchorElement>('.checklist-item__link') as HTMLAnchorElement;
      expect(link.tagName).toBe('A');
      expect(link.getAttribute('href')).toBe('/avaluos/catalogos');
      expect(link.textContent?.trim()).toBe('Ir a 1.5.2 · Catálogos de avalúos');
      const reason = part('.checklist-item__reason') as HTMLElement;
      expect(reason.compareDocumentPosition(link) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('sin texto o sin destino no hay enlace: un enlace sin destino no lleva a ninguna parte', () => {
      expect(render({}).part('.checklist-item__link')).toBeNull();
      expect(render({ linkHref: '/x' }).part('.checklist-item__link')).toBeNull();
      expect(
        render({ link: 'Ir a', linkHref: undefined }).part('.checklist-item__link'),
      ).toBeNull();
      expect(render({ link: 'Ir a', linkHref: '  ' }).part('.checklist-item__link')).toBeNull();
      expect(render({ link: '  ', linkHref: '/x' }).part('.checklist-item__link')).toBeNull();
    });

    it('un clic normal emite navigate con el destino y cancela la navegación del navegador', () => {
      const { page, part } = render(LINK);
      const click = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 });
      part('.checklist-item__link')?.dispatchEvent(click);
      expect(page.navigated).toEqual(['/avaluos/catalogos']);
      expect(click.defaultPrevented).toBe(true);
    });

    it.each(['ctrlKey', 'metaKey', 'shiftKey', 'altKey'] as const)(
      'con %s el navegador abre el enlace como siempre y no se emite navigate',
      (modifier) => {
        const { page, part } = render(LINK);
        const click = new MouseEvent('click', {
          bubbles: true,
          cancelable: true,
          button: 0,
          [modifier]: true,
        });
        part('.checklist-item__link')?.dispatchEvent(click);
        expect(page.navigated).toEqual([]);
        expect(click.defaultPrevented).toBe(false);
      },
    );

    it('el botón central tampoco emite navigate', () => {
      const { page, part } = render(LINK);
      part('.checklist-item__link')?.dispatchEvent(
        new MouseEvent('click', { bubbles: true, cancelable: true, button: 1 }),
      );
      expect(page.navigated).toEqual([]);
    });
  });

  describe('estilos (diseño 64:573)', () => {
    it('el fondo es transparente y cada punto lleva una línea inferior border/default', () => {
      const { item } = render();
      expect(declaredStyle(item, 'border-bottom-width')).toBe('1px');
      // Solo abajo: sin el reinicio del navegador (preflight), `border-solid` pintaría los cuatro lados.
      expect(declaredStyle(item, 'border-top-width')).toBe('0px');
      expect(declaredStyle(item, 'border-left-width')).toBe('0px');
      expect(declaredStyle(item, 'border-right-width')).toBe('0px');
      expect(usesToken(declaredStyle(item, 'border-color'), '--color-border-default')).toBe(true);
    });

    it('número text/muted, título text/primary, motivo text/secondary y enlace action/primary', () => {
      const { part } = render({ link: 'Ir a', linkHref: '/x' });
      const colorOf = (selector: string) => declaredStyle(part(selector) as Element, 'color');
      expect(usesToken(colorOf('.checklist-item__number'), '--color-text-muted')).toBe(true);
      expect(usesToken(colorOf('.checklist-item__title'), '--color-text-primary')).toBe(true);
      expect(usesToken(colorOf('.checklist-item__reason'), '--color-text-secondary')).toBe(true);
      expect(usesToken(colorOf('.checklist-item__link'), '--color-action-primary')).toBe(true);
    });

    it('el icono toma el color del estado: success, text/muted y warning', () => {
      const tokens: Record<ChecklistItemStatus, string> = {
        met: '--color-status-success',
        pending: '--color-text-muted',
        observed: '--color-status-warning',
      };
      for (const status of STATUSES) {
        const icon = render({ status }).part('.checklist-item__icon') as Element;
        expect(usesToken(declaredStyle(icon, 'color'), tokens[status])).toBe(true);
      }
    });

    it('el título es body/md-strong y el número body/md', () => {
      const { part } = render();
      const style = (selector: string, property: string) =>
        declaredStyle(part(selector) as Element, property);
      expect(style('.checklist-item__title', 'font-size')).toMatch(
        /var\(--text-body-md-strong[,)]/,
      );
      expect(style('.checklist-item__title', 'font-weight')).toContain(
        'var(--text-body-md-strong--font-weight',
      );
      expect(style('.checklist-item__number', 'font-size')).toContain('var(--text-body-md,');
      expect(style('.checklist-item__number', 'font-weight')).toContain(
        'var(--text-body-md--font-weight',
      );
    });

    it('el enlace pinta el anillo focus/ring de theme.css solo con el foco del teclado', () => {
      const { part } = render({ link: 'Ir a', linkHref: '/x' });
      const link = part<HTMLAnchorElement>('.checklist-item__link') as HTMLAnchorElement;
      const ring = cssVariable(theme, '--shadow-focus-ring');
      expect(paintsShadow(link, ring)).toBe(false);
      focusWithKeyboard(link);
      expect(paintsShadow(link, ring)).toBe(true);
      link.blur();
      expect(paintsShadow(link, ring)).toBe(false);
    });

    it('el foco del enlace sigue visible en alto contraste (forced-colors)', () => {
      const { part } = render({ link: 'Ir a', linkHref: '/x' });
      const link = part<HTMLAnchorElement>('.checklist-item__link') as HTMLAnchorElement;
      focusWithKeyboard(link);
      expect(declaredStyle(link, 'outline', 'forced-colors')).toContain('solid');
    });

    it('contraste del texto (WCAG 1.4.3, 4.5:1) sobre bg/surface y de los iconos (1.4.11, 3:1)', () => {
      const surface = color('bg-surface');
      for (const text of ['text-muted', 'text-primary', 'text-secondary', 'action-primary']) {
        expect(contrastRatio(color(text), surface)).toBeGreaterThanOrEqual(4.5);
      }
      for (const icon of ['status-success', 'text-muted', 'status-warning']) {
        expect(contrastRatio(color(icon), surface)).toBeGreaterThanOrEqual(3);
      }
    });
  });

  describe('accesibilidad (007-21)', () => {
    const VARIANTS: readonly [string, Partial<HostComponent>][] = [
      ['sin motivo', { reason: undefined }],
      ['con motivo', {}],
      ['opcional', { optional: true }],
      ['con enlace', { link: 'Ir a 1.5.2 · Catálogos', linkHref: '/avaluos' }],
      [
        'opcional, con motivo y enlace',
        { optional: true, link: 'Ir a 1.5.2', linkHref: '/avaluos' },
      ],
      ['sin número', { number: undefined }],
    ];

    it.each(
      STATUSES.flatMap((status) =>
        VARIANTS.map(([name, inputs]) => [status, name, inputs] as const),
      ),
    )('cumple axe %s, %s', async (status, _name, inputs) => {
      await expectNoAxeViolations(render({ ...inputs, status }).list);
    });

    it('cumple axe con el enlace enfocado', async () => {
      const { list, part } = render({ link: 'Ir a', linkHref: '/x', optional: true });
      focusWithKeyboard(part('.checklist-item__link') as HTMLElement);
      await expectNoAxeViolations(list);
    });

    it('cumple axe con varios puntos, de estados distintos, en la misma lista', async () => {
      @Component({
        imports: [ChecklistItemComponent],
        template: `
          <ol role="list">
            @for (point of points; track point.number) {
              <li
                livChecklistItem
                [status]="point.status"
                [number]="point.number"
                [heading]="point.heading"
                [reason]="point.reason"
                [optional]="point.optional"
              ></li>
            }
          </ol>
        `,
      })
      class ManyPoints {
        points = [
          { number: '1.', status: 'met', heading: 'Uno', reason: 'Listo', optional: false },
          { number: '2.', status: 'pending', heading: 'Dos', reason: 'Falta', optional: false },
          { number: '3.', status: 'observed', heading: 'Tres', reason: 'Revisar', optional: true },
        ] as const;
      }
      const fixture = TestBed.createComponent(ManyPoints);
      fixture.detectChanges();
      const list = fixture.nativeElement.querySelector('ol') as HTMLOListElement;
      expect(list.querySelectorAll(':scope > li')).toHaveLength(3);
      await expectNoAxeViolations(list);
    });

    it('cumple axe con un título y un motivo largos, que ocupan varias líneas', async () => {
      await expectNoAxeViolations(
        render({ heading: 'Título '.repeat(40), reason: 'Motivo '.repeat(60) }).list,
      );
    });
  });
});
