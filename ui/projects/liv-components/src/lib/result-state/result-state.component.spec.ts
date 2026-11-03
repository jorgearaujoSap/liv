import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { ResultStateComponent, ResultStateTone } from './result-state.component';

@Component({
  imports: [ResultStateComponent],
  template: `
    <liv-result-state
      [tone]="tone"
      [icon]="icon"
      [eyebrow]="eyebrow"
      [heading]="heading"
      [headingLevel]="headingLevel"
      [description]="description"
    >
      <button type="button">Volver al inicio</button>
    </liv-result-state>
  `,
})
class HostComponent {
  tone: ResultStateTone = 'danger';
  icon: IconName = 'shield-x';
  eyebrow: string | undefined = 'Error 403';
  heading = 'No tienes permiso para ver esta página';
  headingLevel: 1 | 2 | 3 = 2;
  description: string | undefined =
    'Tu rol no incluye esta opción. Si necesitas acceso, solicítalo.';
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('liv-result-state') as HTMLElement;
}

function iconMarkup(name: IconName) {
  const fixture = TestBed.createComponent(IconComponent);
  fixture.componentRef.setInput('name', name);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
}

describe('ResultState', () => {
  it('muestra el icono, la etiqueta, el título, la descripción y las acciones proyectadas', () => {
    const host = render();
    expect(host.querySelector('liv-icon svg')?.innerHTML).toBe(iconMarkup('shield-x'));
    expect(host.querySelector('liv-icon svg')?.getAttribute('width')).toBe('28');
    expect(host.querySelector('.result-state__eyebrow')?.textContent?.trim()).toBe('Error 403');
    expect(host.querySelector('.result-state__title')?.textContent?.trim()).toBe(
      'No tienes permiso para ver esta página',
    );
    expect(host.querySelector('.result-state__description')?.textContent?.trim()).toBe(
      'Tu rol no incluye esta opción. Si necesitas acceso, solicítalo.',
    );
    expect(host.querySelector('.result-state__actions button')?.textContent?.trim()).toBe(
      'Volver al inicio',
    );
  });

  it('la etiqueta y la descripción son opcionales', () => {
    const host = render({ eyebrow: undefined, description: undefined });
    expect(host.querySelector('.result-state__eyebrow')).toBeNull();
    expect(host.querySelector('.result-state__description')).toBeNull();
    expect(render({ eyebrow: ' ' }).querySelector('.result-state__eyebrow')).toBeNull();
  });

  it.each<[ResultStateTone, string, string]>([
    ['danger', '--color-status-danger-subtle', '--color-status-danger,'],
    ['warning', '--color-status-warning-subtle', '--color-status-warning,'],
    ['info', '--color-status-info-subtle', '--color-status-info,'],
    // La coma final distingue `--color-status-danger,` de `--color-status-danger-subtle`.
  ])('%s usa los tokens de Figma en el círculo y en el icono', (tone, tile, iconColor) => {
    const host = render({ tone });
    expect(host.dataset['tone']).toBe(tone);
    const tileElement = host.querySelector('.result-state__tile') as HTMLElement;
    expect(getComputedStyle(tileElement).backgroundColor).toContain(`var(${tile}`);
    expect(getComputedStyle(host.querySelector('liv-icon') as HTMLElement).color).toContain(
      `var(${iconColor}`,
    );
  });

  it('el icono es decorativo: el título dice qué pasó', () => {
    expect(render().querySelector('liv-icon svg')?.getAttribute('aria-hidden')).toBe('true');
  });

  it.each<1 | 2 | 3>([1, 2, 3])('el título es un encabezado de nivel %i', (level) => {
    const title = render({ headingLevel: level }).querySelector('.result-state__title');
    expect(title?.tagName).toBe(`H${level}`);
  });

  it('cumple axe (001-19) en cada tono, con y sin textos opcionales', async () => {
    for (const tone of ['danger', 'warning', 'info'] as ResultStateTone[]) {
      await expectNoAxeViolations(render({ tone }));
    }
    await expectNoAxeViolations(render({ eyebrow: undefined, description: undefined }));
  });
});
