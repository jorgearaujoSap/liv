import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { AlertComponent, AlertStatus } from './alert.component';

@Component({
  imports: [AlertComponent],
  template: `
    <liv-alert [status]="status" [heading]="heading">
      Revisa el usuario y la contraseña e inténtalo de nuevo.
    </liv-alert>
  `,
})
class HostComponent {
  status: AlertStatus = 'error';
  heading?: string = 'No pudimos iniciar tu sesión';
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('liv-alert') as HTMLElement;
}

/** Dibujo de referencia del icono, para comprobar cuál muestra la alerta. */
function iconMarkup(name: IconName) {
  const fixture = TestBed.createComponent(IconComponent);
  fixture.componentRef.setInput('name', name);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
}

describe('Alert', () => {
  it('Error usa role="alert"', () => {
    expect(render({ status: 'error' }).getAttribute('role')).toBe('alert');
  });

  it.each<AlertStatus>(['warning', 'info', 'success'])('%s usa role="status"', (status) => {
    expect(render({ status }).getAttribute('role')).toBe('status');
  });

  it('muestra el título y el mensaje proyectado', () => {
    const host = render();
    expect(host.querySelector('.alert__title')?.textContent?.trim()).toBe(
      'No pudimos iniciar tu sesión',
    );
    expect(host.querySelector('.alert__message')?.textContent?.trim()).toBe(
      'Revisa el usuario y la contraseña e inténtalo de nuevo.',
    );
  });

  it('el título es opcional', () => {
    expect(render({ heading: undefined }).querySelector('.alert__title')).toBeNull();
    expect(render({ heading: ' ' }).querySelector('.alert__title')).toBeNull();
  });

  it.each<[AlertStatus, IconName, string, string]>([
    ['error', 'circle-alert', '--color-status-danger-subtle', '--color-status-danger,'],
    ['warning', 'triangle-alert', '--color-status-warning-subtle', '--color-status-warning,'],
    ['info', 'info', '--color-status-info-subtle', '--color-status-info,'],
    ['success', 'circle-check', '--color-status-success-subtle', '--color-status-success,'],
  ])('%s usa el icono %s y los tokens de Figma', (status, icon, background, iconColor) => {
    const host = render({ status });
    expect(host.dataset['status']).toBe(status);
    expect(getComputedStyle(host).backgroundColor).toContain(`var(${background}`);
    const iconHost = host.querySelector('liv-icon') as HTMLElement;
    expect(iconHost.querySelector('svg')?.innerHTML).toBe(iconMarkup(icon));
    expect(getComputedStyle(iconHost).color).toContain(`var(${iconColor}`);
    expect(iconHost.querySelector('svg')?.getAttribute('width')).toBe('20');
    expect(iconHost.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('cumple axe (001-19) en cada estado, con y sin título', async () => {
    for (const status of ['error', 'warning', 'info', 'success'] as AlertStatus[]) {
      await expectNoAxeViolations(render({ status }));
    }
    await expectNoAxeViolations(render({ heading: undefined }));
  });
});
