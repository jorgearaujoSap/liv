import { Injectable, OnDestroy } from '@angular/core';

import type { SessionStatus } from '../../api/bff/models';

/** Lo que las pestañas del mismo navegador se dicen entre sí sobre la sesión. */
export type SessionMessage =
  /** La sesión terminó (cierre de sesión): las demás pestañas vuelven a iniciar sesión. */
  | { type: 'ended' }
  /** La persona eligió seguir conectada: las demás pestañas cierran su aviso. */
  | { type: 'extended'; status: SessionStatus };

const CHANNEL_NAME = 'liv-session';

/**
 * Canal entre pestañas (`BroadcastChannel`) que sincroniza el aviso de inactividad y el cierre de
 * sesión (001-22). No lleva datos de la persona ni *tokens*, solo lo que le queda a la sesión.
 */
@Injectable({ providedIn: 'root' })
export class SessionChannel implements OnDestroy {
  private readonly channel =
    typeof BroadcastChannel === 'undefined' ? undefined : new BroadcastChannel(CHANNEL_NAME);

  /** Avisa a las demás pestañas; la propia no recibe su mensaje. */
  post(message: SessionMessage): void {
    this.channel?.postMessage(message);
  }

  /** Escucha lo que dicen las demás pestañas. Devuelve la función que deja de escuchar. */
  listen(handler: (message: SessionMessage) => void): () => void {
    const listener = (event: MessageEvent<unknown>) => {
      const message = parse(event.data);
      if (message) {
        handler(message);
      }
    };
    this.channel?.addEventListener('message', listener);
    return () => this.channel?.removeEventListener('message', listener);
  }

  ngOnDestroy(): void {
    this.channel?.close();
  }
}

/** Solo se aceptan mensajes con la forma esperada: el canal es del mismo origen, pero no se confía. */
function parse(data: unknown): SessionMessage | undefined {
  if (typeof data !== 'object' || data === null) {
    return undefined;
  }
  const message = data as Record<string, unknown>;
  if (message['type'] === 'ended') {
    return { type: 'ended' };
  }
  const status = message['status'] as Record<string, unknown> | undefined;
  const idle = status?.['idleSecondsLeft'];
  const absolute = status?.['absoluteSecondsLeft'];
  if (message['type'] === 'extended' && isSeconds(idle) && isSeconds(absolute)) {
    return { type: 'extended', status: { idleSecondsLeft: idle, absoluteSecondsLeft: absolute } };
  }
  return undefined;
}

/** Más que la duración máxima de una sesión (8 horas): un valor mayor no puede ser verdadero. */
const MAX_SECONDS = 24 * 60 * 60;

/** Segundos que pueden venir de otra pestaña: un número finito, no negativo y razonable. */
function isSeconds(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= MAX_SECONDS;
}
