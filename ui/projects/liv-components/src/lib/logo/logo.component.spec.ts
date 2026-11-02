import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { LogoComponent } from './logo.component';

function render(inputs: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(LogoComponent);
  for (const [key, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(key, value);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Logo', () => {
  it('usa el tono Default y muestra el nombre por defecto', () => {
    const host = render();
    expect(host.dataset['tone']).toBe('default');
    expect(host.querySelector('.logo__wordmark')?.textContent).toContain('LIV');
    expect(host.querySelector('.logo__wordmark')?.textContent).toContain('Impuesto vehicular');
  });

  it('admite el tono Inverse', () => {
    expect(render({ tone: 'inverse' }).dataset['tone']).toBe('inverse');
  });

  it('sin nombre queda solo el monograma', () => {
    const host = render({ showWordmark: false });
    expect(host.querySelector('.logo__wordmark')).toBeNull();
    expect(host.querySelector('svg')).not.toBeNull();
  });

  it('dibuja el monograma: cuadrado redondeado, L y punto', () => {
    const svg = render().querySelector('svg') as SVGSVGElement;
    expect(svg.getAttribute('viewBox')).toBe('0 0 36 36');
    expect(svg.querySelector('rect')?.getAttribute('rx')).toBe('9');
    expect(svg.querySelector('polyline')?.getAttribute('points')).toBe('12.5,10 12.5,26 25,26');
    expect(svg.querySelector('circle')?.getAttribute('r')).toBe('2.75');
  });

  it('el punto está donde lo dibuja Figma', () => {
    const dot = render().querySelector('circle') as SVGCircleElement;
    expect([dot.getAttribute('cx'), dot.getAttribute('cy')]).toEqual(['24.5', '11.5']);
  });

  it.each([
    ['default', '--color-action-primary,', '--color-text-inverse', '--color-text-primary'],
    ['inverse', '--color-text-inverse', '--color-action-primary,', '--color-text-inverse'],
  ])('el tono %s pinta monograma y nombre con los tokens de Figma', (tone, tile, letter, name) => {
    const host = render({ tone });
    const style = (selector: string) => getComputedStyle(host.querySelector(selector) as Element);
    expect(style('.logo__tile').fill).toContain(tile);
    expect(style('.logo__letter').stroke).toContain(letter);
    expect(style('.logo__name').color).toContain(name);
  });

  it('se anuncia como imagen con texto alternativo y oculta su contenido', () => {
    const host = render();
    expect(host.getAttribute('role')).toBe('img');
    expect(host.getAttribute('aria-label')).toBe('LIV, impuesto vehicular');
    expect(host.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    expect(host.querySelector('.logo__wordmark')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('el texto alternativo se puede cambiar', () => {
    expect(render({ label: 'Sistema LIV' }).getAttribute('aria-label')).toBe('Sistema LIV');
  });

  it('un texto alternativo vacío vuelve al predeterminado', () => {
    expect(render({ label: '' }).getAttribute('aria-label')).toBe('LIV, impuesto vehicular');
  });

  it('cumple axe con y sin nombre, en ambos tonos', async () => {
    await expectNoAxeViolations(render());
    await expectNoAxeViolations(render({ tone: 'inverse', showWordmark: false }));
  });
});
