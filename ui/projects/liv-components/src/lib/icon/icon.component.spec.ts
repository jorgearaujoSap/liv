import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { ICON_NAMES, IconName, IconSize } from './icons';
import { IconComponent } from './icon.component';

function render(name: IconName, extra: { size?: IconSize; label?: string } = {}) {
  const fixture = TestBed.createComponent(IconComponent);
  fixture.componentRef.setInput('name', name);
  for (const [key, value] of Object.entries(extra)) {
    fixture.componentRef.setInput(key, value);
  }
  fixture.detectChanges();
  const host: HTMLElement = fixture.nativeElement;
  return { host, svg: host.querySelector('svg') as SVGSVGElement };
}

describe('Icon', () => {
  it('tiene los 38 iconos de Lucide de la página 02 Componentes, sin repetir', () => {
    expect(ICON_NAMES).toHaveLength(38);
    expect(new Set(ICON_NAMES).size).toBe(38);
  });

  it.each(ICON_NAMES)('dibuja %s sobre el lienzo de 24 px con trazo de 2 px', (name) => {
    const { svg } = render(name);
    expect(svg.getAttribute('viewBox')).toBe('0 0 24 24');
    expect(svg.getAttribute('stroke')).toBe('currentColor');
    expect(svg.getAttribute('stroke-width')).toBe('2');
    expect(svg.querySelectorAll('path, circle, rect, line').length).toBeGreaterThan(0);
  });

  it('dibuja la forma del icono pedido', () => {
    const { svg } = render('arrow-right');
    const paths = Array.from(svg.querySelectorAll('path')).map((path) => path.getAttribute('d'));
    expect(paths).toEqual(['M5 12h14', 'm12 5 7 7-7 7']);
  });

  it('mide 24 px por defecto y admite 16 y 20', () => {
    expect(render('house').svg.getAttribute('width')).toBe('24');
    const small = render('house', { size: 16 }).svg;
    expect([small.getAttribute('width'), small.getAttribute('height')]).toEqual(['16', '16']);
    const medium = render('house', { size: 20 }).svg;
    expect([medium.getAttribute('width'), medium.getAttribute('height')]).toEqual(['20', '20']);
  });

  it('es decorativo si no tiene texto alternativo (aria-hidden)', () => {
    const { svg } = render('lock');
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.getAttribute('focusable')).toBe('false');
    expect(svg.hasAttribute('role')).toBe(false);
    expect(svg.hasAttribute('aria-label')).toBe(false);
  });

  it('con texto alternativo se anuncia como imagen y deja de ser decorativo', () => {
    const { svg } = render('circle-alert', { label: 'Error' });
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.getAttribute('aria-label')).toBe('Error');
    expect(svg.hasAttribute('aria-hidden')).toBe(false);
  });

  it('cumple axe como decorativo y con texto alternativo', async () => {
    await expectNoAxeViolations(render('lock').host);
    await expectNoAxeViolations(render('circle-alert', { label: 'Error' }).host);
  });
});
