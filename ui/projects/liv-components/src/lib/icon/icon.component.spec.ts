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

const NEW_ICONS: IconName[] = ['plus', 'circle'];

describe('Icon', () => {
  it('tiene los 40 iconos de Lucide de la página 02 Componentes, sin repetir', () => {
    expect(ICON_NAMES).toHaveLength(40);
    expect(new Set(ICON_NAMES).size).toBe(40);
  });

  // Feature 007 (PR 8d, Figma 63:247 y 63:250): `plus` crea una vigencia y `circle` es el estado pendiente.
  // `chevron-left` no entra: es de la paginación (1.6); el total de 40 lo fija.
  it('agrega plus y circle', () => {
    expect(ICON_NAMES).toEqual(expect.arrayContaining(NEW_ICONS));
  });

  it('dibuja plus con las dos líneas de Lucide', () => {
    const paths = Array.from(render('plus').svg.querySelectorAll('path')).map((path) =>
      path.getAttribute('d'),
    );
    expect(paths).toEqual(['M5 12h14', 'M12 5v14']);
  });

  it('dibuja circle como una circunferencia de radio 10 centrada en el lienzo', () => {
    const shapes = Array.from(render('circle').svg.querySelectorAll('path, circle, rect, line'));
    expect(shapes).toHaveLength(1);
    const [circle] = shapes;
    expect(circle.tagName.toLowerCase()).toBe('circle');
    expect(['cx', 'cy', 'r'].map((name) => circle.getAttribute(name))).toEqual(['12', '12', '10']);
  });

  // El color lo da el `stroke="currentColor"` del `svg` (lo comprueba la prueba del lienzo); aquí, que ninguna
  // forma lo tape con un relleno propio.
  it.each(NEW_ICONS)('%s no rellena sus formas: toma el color del contenedor', (name) => {
    for (const shape of Array.from(render(name).svg.querySelectorAll('path, circle, rect, line'))) {
      expect(shape.getAttribute('fill') ?? 'none').toBe('none');
    }
  });

  it.each(ICON_NAMES)('dibuja %s sobre el lienzo de 24 px con trazo de 2 px', (name) => {
    const { svg } = render(name);
    expect(svg.getAttribute('viewBox')).toBe('0 0 24 24');
    expect(svg.getAttribute('stroke')).toBe('currentColor');
    expect(svg.getAttribute('stroke-width')).toBe('2');
    expect([svg.getAttribute('stroke-linecap'), svg.getAttribute('stroke-linejoin')]).toEqual([
      'round',
      'round',
    ]);
    expect(svg.querySelectorAll('path, circle, rect, line').length).toBeGreaterThan(0);
  });

  it('dibuja la forma del icono pedido', () => {
    const { svg } = render('arrow-right');
    const paths = Array.from(svg.querySelectorAll('path')).map((path) => path.getAttribute('d'));
    expect(paths).toEqual(['M5 12h14', 'm12 5 7 7-7 7']);
  });

  it('un nombre desconocido no dibuja formas ni rompe el render', () => {
    for (const name of ['no-existe', 'constructor', 'toString']) {
      expect(
        render(name as IconName).svg.querySelectorAll('path, circle, rect, line'),
      ).toHaveLength(0);
    }
  });

  it('mide 24 px por defecto', () => {
    expect(render('house').svg.getAttribute('width')).toBe('24');
  });

  // 28 px lo usa el círculo de ResultState en Figma (decisión del responsable, 2026-11-02).
  it.each<[IconSize]>([[16], [20], [28]])('admite %i px', (size) => {
    const { svg } = render('house', { size });
    expect([svg.getAttribute('width'), svg.getAttribute('height')]).toEqual([`${size}`, `${size}`]);
  });

  it('a 28 px conserva el lienzo de 24 unidades: el trazo escala con el tamaño', () => {
    expect(render('house', { size: 28 }).svg.getAttribute('viewBox')).toBe('0 0 24 24');
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

  it('un texto alternativo vacío sigue siendo decorativo', () => {
    const { svg } = render('lock', { label: '  ' });
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.hasAttribute('role')).toBe(false);
    expect(svg.hasAttribute('aria-label')).toBe(false);
  });

  it('cumple axe como decorativo y con texto alternativo', async () => {
    await expectNoAxeViolations(render('lock').host);
    await expectNoAxeViolations(render('circle-alert', { label: 'Error' }).host);
  });

  it.each(NEW_ICONS)('cumple axe como decorativo y con texto alternativo: %s', async (name) => {
    await expectNoAxeViolations(render(name).host);
    await expectNoAxeViolations(render(name, { label: 'Texto' }).host);
  });
});
