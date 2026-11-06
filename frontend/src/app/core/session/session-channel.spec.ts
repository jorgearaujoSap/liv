import { vi } from 'vitest';

import { SessionChannel, SessionMessage } from './session-channel';

// 001-22: las pestañas del mismo navegador se sincronizan con el BroadcastChannel real. Estas pruebas
// no lo sustituyen por un doble: dos instancias hablan por el mismo canal, como dos pestañas.
describe('SessionChannel', () => {
  const opened: SessionChannel[] = [];
  const raw: BroadcastChannel[] = [];
  const open = () => {
    const channel = new SessionChannel();
    opened.push(channel);
    return channel;
  };
  const received = (channel: SessionChannel) => {
    const messages: SessionMessage[] = [];
    channel.listen((message) => messages.push(message));
    return messages;
  };

  afterEach(() => {
    opened.splice(0).forEach((channel) => channel.ngOnDestroy());
    raw.splice(0).forEach((channel) => channel.close());
    vi.unstubAllGlobals();
  });

  it('un mensaje de una pestaña llega a las demás', async () => {
    const [a, b, c] = [open(), open(), open()];
    const atB = received(b);
    const atC = received(c);

    a.post({ type: 'ended' });

    await vi.waitFor(() => expect(atB).toEqual([{ type: 'ended' }]));
    await vi.waitFor(() => expect(atC).toEqual([{ type: 'ended' }]));
  });

  it('la pestaña que lo envía no recibe su propio mensaje', async () => {
    const [a, b] = [open(), open()];
    const atA = received(a);
    const atB = received(b);

    a.post({ type: 'ended' });

    await vi.waitFor(() => expect(atB).toHaveLength(1));
    expect(atA).toEqual([]);
  });

  it('«seguir conectado» lleva lo que le queda a la sesión', async () => {
    const [a, b] = [open(), open()];
    const atB = received(b);
    const status = { idleSecondsLeft: 900, absoluteSecondsLeft: 20_000 };

    a.post({ type: 'extended', status });

    await vi.waitFor(() => expect(atB).toEqual([{ type: 'extended', status }]));
  });

  it('deja de escuchar al llamar a la función que devuelve listen()', async () => {
    const [a, b] = [open(), open()];
    const messages: SessionMessage[] = [];
    const stop = b.listen((message) => messages.push(message));
    stop();
    const atB = received(b);

    a.post({ type: 'ended' });

    await vi.waitFor(() => expect(atB).toHaveLength(1));
    expect(messages).toEqual([]);
  });

  describe('lo que no tiene la forma esperada se descarta: el canal es del mismo origen, pero no se confía', () => {
    it.each([
      ['un texto', 'ended'],
      ['null', null],
      ['un tipo desconocido', { type: 'otra-cosa' }],
      ['extended sin estado', { type: 'extended' }],
      ['extended con un estado vacío', { type: 'extended', status: {} }],
      [
        'segundos de texto',
        { type: 'extended', status: { idleSecondsLeft: '5', absoluteSecondsLeft: 5 } },
      ],
      [
        'segundos negativos',
        { type: 'extended', status: { idleSecondsLeft: -1, absoluteSecondsLeft: 5 } },
      ],
      [
        'segundos NaN',
        { type: 'extended', status: { idleSecondsLeft: NaN, absoluteSecondsLeft: 5 } },
      ],
      [
        'segundos de más de un día',
        { type: 'extended', status: { idleSecondsLeft: 1e9, absoluteSecondsLeft: 5 } },
      ],
      [
        'segundos infinitos',
        { type: 'extended', status: { idleSecondsLeft: 5, absoluteSecondsLeft: Infinity } },
      ],
    ])('%s', async (_name, data) => {
      const listener = open();
      const messages = received(listener);
      const sender = new BroadcastChannel('liv-session');
      raw.push(sender);

      sender.postMessage(data);
      // Un mensaje válido detrás del inválido: si llegó, el inválido ya se había procesado.
      sender.postMessage({ type: 'ended' });

      await vi.waitFor(() => expect(messages).toEqual([{ type: 'ended' }]));
    });
  });

  it('si el navegador no deja enviar (canal cerrado), post() no falla: el aviso es lo mejor posible', () => {
    vi.spyOn(BroadcastChannel.prototype, 'postMessage').mockImplementation(() => {
      throw new DOMException('closed', 'InvalidStateError');
    });
    const channel = open();

    expect(() => channel.post({ type: 'ended' })).not.toThrow();
  });

  it('sin BroadcastChannel (navegador antiguo) no falla: no hace nada', () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    const channel = open();
    const stop = channel.listen(() => undefined);

    expect(() => channel.post({ type: 'ended' })).not.toThrow();
    expect(() => stop()).not.toThrow();
  });
});
