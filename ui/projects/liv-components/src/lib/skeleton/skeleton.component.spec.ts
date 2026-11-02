import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { SkeletonComponent } from './skeleton.component';

function render(inputs: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(SkeletonComponent);
  for (const [key, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(key, value);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Skeleton', () => {
  it('usa la forma Text por defecto', () => {
    expect(render().dataset['shape']).toBe('text');
  });

  it.each(['text', 'circle', 'block'])('admite la forma %s', (shape) => {
    expect(render({ shape }).dataset['shape']).toBe(shape);
  });

  it('sin medidas usa las del diseño', () => {
    const host = render();
    expect(host.style.width).toBe('');
    expect(host.style.height).toBe('');
  });

  it('se redimensiona según el contenido real', () => {
    const host = render({ width: '50%', height: '1.5rem' });
    expect(host.style.width).toBe('50%');
    expect(host.style.height).toBe('1.5rem');
  });

  it('es decorativo: el contenedor es quien anuncia aria-busy', () => {
    expect(render().getAttribute('aria-hidden')).toBe('true');
  });

  it('cumple axe', async () => {
    await expectNoAxeViolations(render({ shape: 'block' }));
  });
});
