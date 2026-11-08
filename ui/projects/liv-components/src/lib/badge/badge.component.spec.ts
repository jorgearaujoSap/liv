import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { BadgeComponent, BadgeTone } from './badge.component';

@Component({
  imports: [BadgeComponent],
  template: `<liv-badge [tone]="tone" [showDot]="showDot">Próximamente</liv-badge>`,
})
class HostComponent {
  tone: BadgeTone = 'neutral';
  showDot = false;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('liv-badge') as HTMLElement;
}

describe('Badge', () => {
  it('muestra el texto proyectado como etiqueta', () => {
    expect(render().querySelector('.badge__label')?.textContent?.trim()).toBe('Próximamente');
  });

  it.each<BadgeTone>(['neutral', 'primary', 'success', 'warning', 'danger', 'info'])(
    'admite el tono %s',
    (tone) => {
      expect(render({ tone }).dataset['tone']).toBe(tone);
    },
  );

  it('usa el tono Neutral por defecto', () => {
    const fixture = TestBed.createComponent(BadgeComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).dataset['tone']).toBe('neutral');
  });

  it('no muestra el punto salvo que se pida, y entonces es decorativo', () => {
    expect(render().querySelector('.badge__dot')).toBeNull();
    const dot = render({ showDot: true }).querySelector('.badge__dot');
    expect(dot).not.toBeNull();
    expect(dot?.getAttribute('aria-hidden')).toBe('true');
  });

  it.each<[BadgeTone, string, string]>([
    ['neutral', '--color-bg-subtle', '--color-text-secondary'],
    ['primary', '--color-action-primary-subtle', '--color-action-primary'],
    ['success', '--color-status-success-subtle', '--color-status-success'],
    ['warning', '--color-status-warning-subtle', '--color-status-warning'],
    ['danger', '--color-status-danger-subtle', '--color-status-danger'],
    ['info', '--color-status-info-subtle', '--color-status-info'],
  ])('el tono %s usa los tokens de fondo y de texto de Figma', (tone, background, text) => {
    const style = getComputedStyle(render({ tone }));
    expect(style.backgroundColor).toContain(`var(${background},`);
    expect(style.color).toContain(`var(${text},`);
  });

  it('el punto toma el color del texto', () => {
    const host = render({ tone: 'danger', showDot: true });
    const dot = host.querySelector('.badge__dot') as HTMLElement;
    expect(getComputedStyle(dot).backgroundColor).toBe(getComputedStyle(host).color);
  });

  it('cumple axe con y sin punto', async () => {
    await expectNoAxeViolations(render());
    await expectNoAxeViolations(render({ tone: 'danger', showDot: true }));
  });
});

describe('demostración 003', () => {
  it('falla a propósito', () => {
    expect(1).toBe(2);
  });
});
