import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { IdleService, type SessionWarning } from '../../core/session/idle.service';
import { SessionWarningDialog } from './session-warning-dialog';

const text = (element: Element | null | undefined) =>
  element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

class FakeIdle {
  readonly warning = signal<SessionWarning | undefined>(undefined);
  extend = vi.fn().mockResolvedValue(true);
  readonly logout = vi.fn().mockResolvedValue(undefined);
}

// 001-22 (WCAG 2.2.1): el aviso permite seguir conectado o cerrar sesión.
describe('aviso de sesión por expirar (001-22)', () => {
  let idle: FakeIdle;

  const render = () => {
    const fixture = TestBed.createComponent(SessionWarningDialog);
    fixture.detectChanges();
    return fixture;
  };
  const dialog = (fixture: { nativeElement: unknown }) =>
    (fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]');
  const button = (root: Element | null, label: string) =>
    Array.from(root?.querySelectorAll('button') ?? []).find((b) => text(b) === label);

  beforeEach(() => {
    idle = new FakeIdle();
    TestBed.configureTestingModule({ providers: [{ provide: IdleService, useValue: idle }] });
  });

  it('cerrado mientras no haya nada que avisar', () => {
    expect(dialog(render())).toBeNull();
  });

  it('por inactividad: avisa a los 2 minutos y ofrece cerrar sesión o seguir conectado', () => {
    idle.warning.set({ kind: 'idle', secondsLeft: 120 });
    const fixture = render();

    const open = dialog(fixture);
    expect(open?.getAttribute('aria-modal')).toBe('true');
    expect(text(open)).toContain('Tu sesión está por expirar');
    expect(text(open)).toContain(
      'Por seguridad cerraremos tu sesión en 2 minutos por inactividad. ¿Quieres seguir conectado?',
    );
    expect(Array.from(open?.querySelectorAll('button') ?? []).map(text)).toEqual([
      'Cerrar sesión',
      'Seguir conectado',
    ]);
  });

  it('cuenta los minutos que faltan, redondeando hacia arriba, sin cambiar cada segundo', () => {
    const minutes = (secondsLeft: number) => {
      idle.warning.set({ kind: 'idle', secondsLeft });
      const fixture = render();
      const result = text(dialog(fixture)).match(/en (\d+ minutos?) por/)?.[1];
      fixture.destroy();
      return result;
    };

    expect(minutes(120)).toBe('2 minutos');
    expect(minutes(119)).toBe('2 minutos');
    expect(minutes(61)).toBe('2 minutos');
    expect(minutes(60)).toBe('1 minuto');
    expect(minutes(1)).toBe('1 minuto');
    expect(minutes(0)).toBe('1 minuto');
  });

  it('«Seguir conectado» extiende la sesión y el botón queda ocupado mientras tanto', async () => {
    let finish: (renewed: boolean) => void = () => undefined;
    idle.extend = vi.fn(() => new Promise<boolean>((resolve) => (finish = resolve)));
    idle.warning.set({ kind: 'idle', secondsLeft: 90 });
    const fixture = render();
    const keep = button(dialog(fixture), 'Seguir conectado') as HTMLButtonElement;

    keep.click();
    keep.click();
    fixture.detectChanges();

    expect(idle.extend).toHaveBeenCalledOnce();
    expect(keep.getAttribute('aria-disabled')).toBe('true');

    finish(true);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(keep.getAttribute('aria-disabled')).not.toBe('true');
  });

  it('el foco inicial queda en «Seguir conectado»', async () => {
    idle.warning.set({ kind: 'idle', secondsLeft: 120 });
    const fixture = render();
    await fixture.whenStable();

    expect(document.activeElement).toBe(button(dialog(fixture), 'Seguir conectado'));
  });

  it('«Cerrar sesión» sale', () => {
    idle.warning.set({ kind: 'idle', secondsLeft: 120 });
    const fixture = render();

    (button(dialog(fixture), 'Cerrar sesión') as HTMLButtonElement).click();

    expect(idle.logout).toHaveBeenCalledOnce();
    expect(idle.extend).not.toHaveBeenCalled();
  });

  it('Escape no lo cierra: la persona elige una de las dos acciones', () => {
    idle.warning.set({ kind: 'idle', secondsLeft: 120 });
    const fixture = render();

    dialog(fixture)?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(dialog(fixture)).not.toBeNull();
    expect(idle.extend).not.toHaveBeenCalled();
    expect(idle.logout).not.toHaveBeenCalled();
  });

  describe('límite de 8 horas', () => {
    beforeEach(() => idle.warning.set({ kind: 'absolute', secondsLeft: 300 }));

    it('avisa del límite sin ofrecer seguir conectado, porque no serviría', () => {
      const open = dialog(render());

      expect(text(open)).toContain('Tu sesión está por terminar');
      expect(text(open)).toContain(
        'Por seguridad, ninguna sesión dura más de 8 horas. Cerraremos la tuya en 5 minutos. Guarda lo que estés haciendo y vuelve a iniciar sesión.',
      );
      expect(Array.from(open?.querySelectorAll('button') ?? []).map(text)).toEqual([
        'Cerrar sesión',
      ]);
    });

    it('«Cerrar sesión» sale', () => {
      const fixture = render();

      (button(dialog(fixture), 'Cerrar sesión') as HTMLButtonElement).click();

      expect(idle.logout).toHaveBeenCalledOnce();
    });
  });
});
