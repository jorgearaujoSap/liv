import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { SpinnerComponent } from './spinner.component';

function render(inputs: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(SpinnerComponent);
  for (const [key, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(key, value);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Spinner', () => {
  it('usa el tono Default y 20 px por defecto', () => {
    const host = render();
    const svg = host.querySelector('svg') as SVGSVGElement;
    expect(host.dataset['tone']).toBe('default');
    expect([svg.getAttribute('width'), svg.getAttribute('height')]).toEqual(['20', '20']);
  });

  it('admite el tono Inverse y los tamaños 16 y 24', () => {
    expect(render({ tone: 'inverse' }).dataset['tone']).toBe('inverse');
    expect(render({ size: 16 }).querySelector('svg')?.getAttribute('width')).toBe('16');
    expect(render({ size: 24 }).querySelector('svg')?.getAttribute('width')).toBe('24');
  });

  it('dibuja la pista y el arco de 135 grados', () => {
    const svg = render().querySelector('svg') as SVGSVGElement;
    expect(svg.getAttribute('viewBox')).toBe('0 0 20 20');
    expect(svg.querySelector('.spinner__track')?.getAttribute('r')).toBe('8.75');
    expect(svg.querySelector('.spinner__arc')?.getAttribute('d')).toBe(
      'M10 1.25A8.75 8.75 0 0 1 16.187 16.187',
    );
  });

  it('avisa lo que carga con aria-live polite y un texto', () => {
    const host = render();
    expect(host.getAttribute('role')).toBe('status');
    expect(host.getAttribute('aria-live')).toBe('polite');
    expect(host.querySelector('.spinner__label')?.textContent).toBe('Cargando');
    expect(render({ label: 'Cargando avalúos' }).textContent).toContain('Cargando avalúos');
  });

  it('el dibujo es decorativo', () => {
    expect(render().querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('cumple axe', async () => {
    await expectNoAxeViolations(render());
    await expectNoAxeViolations(render({ tone: 'inverse', label: 'Ingresando' }));
  });
});
