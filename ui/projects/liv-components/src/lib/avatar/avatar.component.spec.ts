import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { AvatarComponent } from './avatar.component';

function render(inputs: Record<string, unknown> = { initials: 'LM' }) {
  const fixture = TestBed.createComponent(AvatarComponent);
  for (const [key, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(key, value);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Avatar', () => {
  it('muestra las iniciales', () => {
    expect(render().textContent?.trim()).toBe('LM');
  });

  it('usa el tamaño Sm por defecto', () => {
    expect(render().dataset['size']).toBe('sm');
  });

  it.each(['sm', 'md', 'lg'])('admite el tamaño %s', (size) => {
    expect(render({ initials: 'LM', size }).dataset['size']).toBe(size);
  });

  it.each([
    ['sm', '--spacing-6', '--text-label-sm'],
    ['md', '--spacing-8', '--text-label-sm'],
    ['lg', '--spacing-10', '--text-label-md'],
  ])('el tamaño %s mide lo que dice Figma y usa su estilo de texto', (size, spacing, text) => {
    const style = getComputedStyle(render({ initials: 'LM', size }));
    expect(style.width).toContain(`var(${spacing},`);
    expect(style.height).toContain(`var(${spacing},`);
    expect(style.fontSize).toContain(`var(${text},`);
    expect(style.backgroundColor).toContain('--color-action-primary-subtle');
    expect(style.color).toContain('--color-action-primary,');
  });

  it('es decorativo: el nombre completo aparece junto a él', () => {
    expect(render().getAttribute('aria-hidden')).toBe('true');
  });

  it('cumple axe', async () => {
    await expectNoAxeViolations(render({ initials: 'JA', size: 'lg' }));
  });
});
