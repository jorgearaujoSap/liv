import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { BrowserNavigation } from './browser-navigation.service';

/** Un documento a medida: jsdom no navega ni simula la precarga del navegador. */
class FakeDocument {
  prerendering = false;
  readonly replaced: string[] = [];
  private listener?: () => void;
  readonly defaultView = { location: { replace: (url: string) => this.replaced.push(url) } };

  addEventListener = vi.fn((type: string, listener: () => void) => {
    if (type === 'prerenderingchange') {
      this.listener = listener;
    }
  });

  /** El navegador muestra la página precargada: la persona la abrió. */
  activate(): void {
    this.prerendering = false;
    this.listener?.();
  }
}

// 001-01: el navegador puede precargar la página mientras se escribe la dirección. En esa precarga no se
// navega a /bff/login, que el BFF no atiende (no abre un inicio de sesión que la persona no pidió): se
// espera a que la persona abra la página.
describe('BrowserNavigation', () => {
  let document: FakeDocument;
  let navigation: BrowserNavigation;

  beforeEach(() => {
    document = new FakeDocument();
    TestBed.configureTestingModule({ providers: [{ provide: DOCUMENT, useValue: document }] });
    navigation = TestBed.inject(BrowserNavigation);
  });

  it('navega de inmediato en una página normal', () => {
    navigation.replace('/bff/login?returnTo=%2F');

    expect(document.replaced).toEqual(['/bff/login?returnTo=%2F']);
  });

  it('durante la precarga no navega: espera a que la persona abra la página', () => {
    document.prerendering = true;

    navigation.replace('/bff/login?returnTo=%2F');
    expect(document.replaced).toEqual([]);

    document.activate();
    expect(document.replaced).toEqual(['/bff/login?returnTo=%2F']);
  });

  it('espera una sola vez: el aviso del navegador no se escucha para siempre', () => {
    document.prerendering = true;

    navigation.replace('/');

    expect(document.addEventListener).toHaveBeenCalledWith(
      'prerenderingchange',
      expect.any(Function),
      { once: true },
    );
  });
});
