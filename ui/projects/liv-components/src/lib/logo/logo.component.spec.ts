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

  it('cumple axe con y sin nombre, en ambos tonos', async () => {
    await expectNoAxeViolations(render());
    await expectNoAxeViolations(render({ tone: 'inverse', showWordmark: false }));
  });
});
