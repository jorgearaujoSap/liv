import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { contrastRatio, solidColor } from '../../testing/contrast';
import { cssVariable, declaredStyle, usesToken } from '../../testing/css';
import { PanelComponent } from './panel.component';

@Component({
  imports: [PanelComponent],
  template: `
    <liv-panel [heading]="heading" [description]="description" [headingLevel]="headingLevel">
      <p class="projected">Contenido del panel</p>
    </liv-panel>
  `,
})
class HostComponent {
  heading = 'Datos de la vigencia';
  description: string | undefined = 'La vigencia va del 1 de enero al 31 de diciembre del año.';
  headingLevel: 2 | 3 = 2;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const host = fixture.nativeElement.querySelector('liv-panel') as HTMLElement;
  const part = <T extends Element = HTMLElement>(selector: string) =>
    host.querySelector<T>(selector);
  return { fixture, host, part };
}

const color = (name: string) => solidColor(cssVariable(theme, `--color-${name}`));

describe('Panel', () => {
  it('es una región (section) cuyo nombre accesible es su título', () => {
    const { part } = render();
    const section = part('section') as HTMLElement;
    const heading = part('.panel__heading') as HTMLElement;
    expect(heading.id).not.toBe('');
    expect(section.getAttribute('aria-labelledby')).toBe(heading.id);
    expect(heading.textContent?.trim()).toBe('Datos de la vigencia');
  });

  describe('título', () => {
    it('es un h2 por omisión', () => {
      expect(render().part('.panel__heading')?.tagName).toBe('H2');
    });

    it.each<[2 | 3, string]>([
      [2, 'H2'],
      [3, 'H3'],
    ])(
      'con headingLevel %s es un %s, según el nivel que toca en la página',
      (headingLevel, tag) => {
        expect(render({ headingLevel }).part('.panel__heading')?.tagName).toBe(tag);
      },
    );

    it('cada panel tiene su propio identificador', () => {
      const ids = [render(), render()].map(({ part }) => part('.panel__heading')?.id);
      expect(ids[0]).not.toBe(ids[1]);
    });
  });

  describe('Description', () => {
    it('se muestra debajo del título', () => {
      const { part } = render();
      const description = part('.panel__description') as HTMLElement;
      expect(description.textContent?.trim()).toBe(
        'La vigencia va del 1 de enero al 31 de diciembre del año.',
      );
      const heading = part('.panel__heading') as HTMLElement;
      expect(
        heading.compareDocumentPosition(description) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    it('sin descripción, o con una vacía, no se muestra (Show description)', () => {
      expect(render({ description: undefined }).part('.panel__description')).toBeNull();
      expect(render({ description: '' }).part('.panel__description')).toBeNull();
      expect(render({ description: '   ' }).part('.panel__description')).toBeNull();
    });
  });

  it('enlaza la descripción con aria-describedby; sin descripción no hay enlace', () => {
    const { part } = render();
    const section = part('section') as HTMLElement;
    const description = part('.panel__description') as HTMLElement;
    expect(description.id).not.toBe('');
    expect(section.getAttribute('aria-describedby')).toBe(description.id);
    expect(
      render({ description: undefined }).part('section')?.hasAttribute('aria-describedby'),
    ).toBe(false);
  });

  it('proyecta su contenido después del encabezado, dentro de la región', () => {
    const { part } = render();
    const content = part('.panel__content') as HTMLElement;
    const projected = part('.projected') as HTMLElement;
    expect(content.contains(projected)).toBe(true);
    expect(part('section')?.contains(content)).toBe(true);
    const header = part('.panel__header') as HTMLElement;
    expect(header.compareDocumentPosition(content) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(header.contains(projected)).toBe(false);
  });

  describe('estilos (diseño 64:631)', () => {
    it('es una superficie bg/surface con borde border/default de 1 px y radio 12', () => {
      const surface = render().part('section') as HTMLElement;
      expect(usesToken(declaredStyle(surface, 'background-color'), '--color-bg-surface')).toBe(
        true,
      );
      expect(declaredStyle(surface, 'border-width')).toBe('1px');
      expect(usesToken(declaredStyle(surface, 'border-color'), '--color-border-default')).toBe(
        true,
      );
      expect(declaredStyle(surface, 'border-radius')).toMatch(/var\(--radius-xl[,)]/);
    });

    it('tiene 24 px de relleno y 16 px entre el encabezado y el contenido', () => {
      const surface = render().part('section') as HTMLElement;
      expect(declaredStyle(surface, 'padding')).toMatch(/var\(--spacing-6[,)]/);
      expect(declaredStyle(surface, 'display')).toBe('flex');
      expect(declaredStyle(surface, 'flex-direction')).toBe('column');
      expect(declaredStyle(surface, 'gap')).toMatch(/var\(--spacing-4[,)]/);
    });

    it('el título es heading/h3 en text/primary y la descripción body/md en text/secondary', () => {
      const { part } = render();
      const heading = part('.panel__heading') as Element;
      const description = part('.panel__description') as Element;
      expect(usesToken(declaredStyle(heading, 'color'), '--color-text-primary')).toBe(true);
      expect(declaredStyle(heading, 'font-size')).toMatch(/var\(--text-heading-h3[,)]/);
      expect(usesToken(declaredStyle(description, 'color'), '--color-text-secondary')).toBe(true);
      expect(declaredStyle(description, 'font-size')).toMatch(/var\(--text-body-md[,)]/);
    });

    it('el título es h3 en el diseño aunque sea un h2: el nivel no cambia su aspecto', () => {
      const sizes = ([2, 3] as const).map((headingLevel) =>
        declaredStyle(render({ headingLevel }).part('.panel__heading') as Element, 'font-size'),
      );
      expect(sizes[0]).toBe(sizes[1]);
    });

    it('los títulos del navegador no dejan márgenes propios', () => {
      const heading = render().part('.panel__heading') as Element;
      expect(declaredStyle(heading, 'margin')).toMatch(/var\(--spacing-0[,)]/);
    });

    it('en alto contraste (forced-colors) el borde delimita la superficie', () => {
      const surface = render().part('section') as HTMLElement;
      expect(declaredStyle(surface, 'border-style')).toBe('solid');
    });

    it('contraste del texto (WCAG 1.4.3, 4.5:1) sobre bg/surface', () => {
      for (const text of ['text-primary', 'text-secondary']) {
        expect(contrastRatio(color(text), color('bg-surface'))).toBeGreaterThanOrEqual(4.5);
      }
    });
  });

  describe('accesibilidad (007-21)', () => {
    it.each<[string, Partial<HostComponent>]>([
      ['con título y descripción', {}],
      ['sin descripción', { description: undefined }],
      ['con un título h3', { headingLevel: 3 }],
      [
        'con un título y una descripción largos',
        { heading: 'Título '.repeat(30), description: 'x '.repeat(120) },
      ],
    ])('cumple axe %s', async (_name, inputs) => {
      await expectNoAxeViolations(render(inputs).host);
    });

    it('cumple axe con dos paneles en la página, cada uno con su región', async () => {
      @Component({
        imports: [PanelComponent],
        template: `
          <h1>Vigencia 2027</h1>
          <liv-panel heading="Datos de la vigencia"><p>Uno</p></liv-panel>
          <liv-panel
            heading="Lista de preparación"
            description="Cada punto lo verifica el sistema."
          >
            <p>Dos</p>
          </liv-panel>
        `,
      })
      class TwoPanels {}
      const fixture = TestBed.createComponent(TwoPanels);
      fixture.detectChanges();
      await expectNoAxeViolations(fixture.nativeElement);
    });
  });
});
