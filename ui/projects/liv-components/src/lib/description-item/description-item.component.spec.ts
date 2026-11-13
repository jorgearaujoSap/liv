import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { contrastRatio, solidColor } from '../../testing/contrast';
import { cssVariable, declaredStyle, usesToken } from '../../testing/css';
import { BadgeTone } from '../badge/badge.component';
import { DescriptionItemComponent, DescriptionItemType } from './description-item.component';

interface ItemInputs {
  type?: DescriptionItemType;
  label: string;
  value: string;
  tone?: BadgeTone;
}

@Component({
  imports: [DescriptionItemComponent],
  template: `
    <dl>
      @for (item of items; track item.label) {
        <div livDescriptionItem [type]="item.type ?? 'text'" [label]="item.label" [value]="item.value" [tone]="item.tone ?? 'primary'"></div>
      }
    </dl>
  `,
})
class HostComponent {
  items: ItemInputs[] = [{ label: 'Año', value: '2026' }];
}

function render(items: ItemInputs[] = [{ label: 'Año', value: '2026' }]) {
  const fixture = TestBed.createComponent(HostComponent);
  fixture.componentInstance.items = items;
  fixture.detectChanges();
  const list = fixture.nativeElement.querySelector('dl') as HTMLDListElement;
  const entries = Array.from(list.querySelectorAll<HTMLElement>('[livDescriptionItem]'));
  const item = entries[0];
  const part = <T extends Element = HTMLElement>(selector: string) =>
    item.querySelector<T>(selector);
  return { fixture, list, entries, item, part };
}

const color = (name: string) => solidColor(cssVariable(theme, `--color-${name}`));

describe('DescriptionItem', () => {
  it('es un grupo de una lista de descripción: un div con su dt (etiqueta) y su dd (valor)', () => {
    const { list, item } = render();
    expect(item.tagName).toBe('DIV');
    expect(item.parentElement).toBe(list);
    expect(item.children).toHaveLength(2);
    expect(item.children[0].tagName).toBe('DT');
    expect(item.children[1].tagName).toBe('DD');
    expect(item.children[0].textContent?.trim()).toBe('Año');
    expect(item.children[1].textContent?.trim()).toBe('2026');
  });

  it('Text es el tipo por omisión y no dibuja ningún Badge', () => {
    const fixture = TestBed.createComponent(DescriptionItemComponent);
    fixture.componentRef.setInput('label', 'Año');
    fixture.componentRef.setInput('value', '2026');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.dataset['type']).toBe('text');
    expect(host.querySelector('liv-badge')).toBeNull();
  });

  describe('Type=Text', () => {
    it('el valor es texto simple', () => {
      const { item, part } = render([{ type: 'text', label: 'Fin', value: '31 de diciembre de 2026' }]);
      expect(item.dataset['type']).toBe('text');
      expect(part('dd')?.textContent?.trim()).toBe('31 de diciembre de 2026');
      expect(part('liv-badge')).toBeNull();
    });
  });

  describe('Type=Badge', () => {
    it('el valor va dentro de un Badge, con el texto del estado', () => {
      const { item, part } = render([{ type: 'badge', label: 'Estado', value: 'En preparación' }]);
      expect(item.dataset['type']).toBe('badge');
      const badge = part('dd liv-badge') as HTMLElement;
      expect(badge.textContent?.trim()).toBe('En preparación');
      expect(part('dt')?.textContent?.trim()).toBe('Estado');
    });

    it('el tono por omisión es Primary, el del diseño, y admite los demás', () => {
      const tones: BadgeTone[] = ['neutral', 'primary', 'success', 'warning', 'danger', 'info'];
      expect(
        (render([{ type: 'badge', label: 'Estado', value: 'En preparación' }]).part('liv-badge') as HTMLElement)
          .dataset['tone'],
      ).toBe('primary');
      for (const tone of tones) {
        const badge = render([{ type: 'badge', label: 'Estado', value: 'Planeada', tone }]).part(
          'liv-badge',
        ) as HTMLElement;
        expect(badge.dataset['tone']).toBe(tone);
      }
    });

    it('en Text, el tono no tiene efecto', () => {
      expect(render([{ label: 'Año', value: '2026', tone: 'danger' }]).part('liv-badge')).toBeNull();
    });
  });

  it('varios elementos forman una sola lista, en el orden dado', () => {
    const { entries } = render([
      { label: 'Año', value: '2026' },
      { label: 'Inicio', value: '1 de enero de 2026' },
      { type: 'badge', label: 'Estado', value: 'Planeada', tone: 'neutral' },
    ]);
    expect(entries.map((entry) => entry.querySelector('dt')?.textContent?.trim())).toEqual([
      'Año',
      'Inicio',
      'Estado',
    ]);
  });

  describe('estilos (diseño 64:583)', () => {
    it('apila la etiqueta sobre el valor con 4 px de separación', () => {
      const { item } = render();
      expect(declaredStyle(item, 'display')).toBe('flex');
      expect(declaredStyle(item, 'flex-direction')).toBe('column');
      expect(declaredStyle(item, 'gap')).toContain('var(--spacing-1');
    });

    it('la etiqueta es label/sm en text/secondary y el valor body/md en text/primary', () => {
      const { part } = render();
      const dt = part('dt') as Element;
      const dd = part('dd') as Element;
      expect(usesToken(declaredStyle(dt, 'color'), '--color-text-secondary')).toBe(true);
      expect(declaredStyle(dt, 'font-size')).toContain('var(--text-label-sm');
      expect(usesToken(declaredStyle(dd, 'color'), '--color-text-primary')).toBe(true);
      expect(declaredStyle(dd, 'font-size')).toContain('var(--text-body-md');
    });

    it('los navegadores dan un margen a dd: se quita', () => {
      const dd = render().part('dd') as Element;
      expect(declaredStyle(dd, 'margin')).toBe('0px');
    });

    it('un valor largo se parte en lugar de desbordar', () => {
      const dd = render().part('dd') as Element;
      expect(declaredStyle(dd, 'overflow-wrap')).toBe('anywhere');
    });

    it('contraste del texto (WCAG 1.4.3, 4.5:1) sobre bg/surface', () => {
      for (const text of ['text-secondary', 'text-primary']) {
        expect(contrastRatio(color(text), color('bg-surface'))).toBeGreaterThanOrEqual(4.5);
      }
    });
  });

  describe('accesibilidad (007-21)', () => {
    it.each<[string, ItemInputs[]]>([
      ['con un Text', [{ label: 'Año', value: '2026' }]],
      ['con un Badge', [{ type: 'badge', label: 'Estado', value: 'En preparación' }]],
      [
        'con un Badge de cada tono',
        (['neutral', 'primary', 'success', 'warning', 'danger', 'info'] as BadgeTone[]).map(
          (tone) => ({ type: 'badge' as const, label: `Estado ${tone}`, value: tone, tone }),
        ),
      ],
      [
        'con los cinco datos de una vigencia',
        [
          { label: 'Año', value: '2027' },
          { label: 'Inicio', value: '1 de enero de 2027' },
          { label: 'Fin', value: '31 de diciembre de 2027' },
          { type: 'badge', label: 'Estado', value: 'Planeada', tone: 'neutral' },
          { label: 'Liquidación', value: 'No habilitada' },
        ],
      ],
      ['con un valor largo', [{ label: 'Nota', value: 'palabra '.repeat(60) }]],
    ])('cumple axe %s', async (_name, items) => {
      await expectNoAxeViolations(render(items).list);
    });
  });
});
