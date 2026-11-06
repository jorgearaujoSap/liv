import { vi } from 'vitest';

import { startWhenOpened, whenOpened } from './when-opened';

/** Un documento a medida: jsdom no simula la precarga del navegador. */
function fakeDocument(prerendering?: boolean) {
  const listeners: { listener: () => void; once: boolean }[] = [];
  const document = {
    addEventListener: vi.fn(
      (type: string, listener: () => void, options?: AddEventListenerOptions) => {
        if (type === 'prerenderingchange') {
          listeners.push({ listener, once: options?.once === true });
        }
      },
    ),
    /** El navegador muestra la página precargada: la persona la abrió. */
    activate() {
      if (prerendering !== undefined) {
        Object.assign(document, { prerendering: false });
      }
      for (const entry of [...listeners]) {
        entry.listener();
        if (entry.once) {
          listeners.splice(listeners.indexOf(entry), 1);
        }
      }
    },
    listeners,
  };
  if (prerendering !== undefined) {
    Object.assign(document, { prerendering });
  }
  return document;
}

const settled = async (promise: Promise<void>) => {
  let done = false;
  void promise.then(() => (done = true));
  await Promise.resolve();
  await Promise.resolve();
  return done;
};

// 001-01: el navegador puede precargar la página mientras se escribe la dirección. La aplicación no arranca
// hasta que la persona la abre: así decide con lo que hay en ese momento (la sesión pudo abrirse o cerrarse
// mientras tanto) y la precarga no pide nada al BFF ni avisa a otras pestañas.
describe('whenOpened (001-01)', () => {
  it('en un navegador sin precarga (Firefox, Safari) sigue de inmediato', async () => {
    expect(await settled(whenOpened(fakeDocument() as unknown as Document))).toBe(true);
  });

  it('en una página normal sigue de inmediato', async () => {
    expect(await settled(whenOpened(fakeDocument(false) as unknown as Document))).toBe(true);
  });

  it('durante la precarga espera a que la persona abra la página, y escucha una sola vez', async () => {
    const document = fakeDocument(true);
    const opened = whenOpened(document as unknown as Document);

    expect(await settled(opened)).toBe(false);
    document.activate();
    expect(await settled(opened)).toBe(true);
    expect(document.listeners).toEqual([]);
  });
});

describe('startWhenOpened (001-01)', () => {
  it('durante la precarga no arranca la aplicación: arranca cuando la persona abre la página', async () => {
    const document = fakeDocument(true);
    const bootstrap = vi.fn(() => Promise.resolve());

    const started = startWhenOpened(document as unknown as Document, bootstrap);
    await settled(Promise.resolve());
    expect(bootstrap).not.toHaveBeenCalled();

    document.activate();
    await started;
    expect(bootstrap).toHaveBeenCalledTimes(1);
  });

  it('sin precarga arranca de inmediato', async () => {
    const bootstrap = vi.fn(() => Promise.resolve());

    await startWhenOpened(fakeDocument() as unknown as Document, bootstrap);

    expect(bootstrap).toHaveBeenCalledTimes(1);
  });
});
