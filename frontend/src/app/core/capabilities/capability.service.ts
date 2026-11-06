import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';

import { getCapabilities } from '../../api/platform/functions';
import type { Capabilities, Capability } from '../../api/platform/models';
import { PlatformApi } from '../../api/platform/platform-api';
import { SKIP_FORBIDDEN_HANDLING } from '../http/api-context';

/**
 * Las opciones de menú que el backend permite al usuario (`/api/v1/me/capabilities`). El backend decide
 * qué se ve; aquí solo se guarda y se consulta (001-15, 001-16).
 */
@Injectable({ providedIn: 'root' })
export class CapabilityService {
  private readonly platform = inject(PlatformApi);
  private pending?: Promise<Capabilities>;
  private reloading?: Promise<Capabilities>;
  /** Número de la última petición: una respuesta de una anterior no pisa a la más reciente. */
  private latest = 0;

  private readonly current = signal<Capabilities | undefined>(undefined);
  /** Lo último que se cargó; `undefined` mientras no se haya cargado. */
  readonly capabilities = this.current.asReadonly();

  /** Las capacidades, cargadas una sola vez; un fallo no se guarda y el próximo intento vuelve a pedirlas. */
  load(): Promise<Capabilities> {
    this.pending ??= this.request();
    return this.pending;
  }

  /**
   * Vuelve a pedirlas, por ejemplo tras un 403: los permisos pudieron cambiar. Varias recargas a la vez
   * comparten una sola petición.
   */
  reload(): Promise<Capabilities> {
    this.reloading ??= this.request().finally(() => (this.reloading = undefined));
    this.pending = this.reloading;
    return this.reloading;
  }

  /** Busca una opción por su código (`1`, `1.5` o `1.5.9`) en lo ya cargado. */
  find(code: string): Capability | undefined {
    return search(this.current()?.items ?? [], code);
  }

  private async request(): Promise<Capabilities> {
    const number = ++this.latest;
    try {
      const capabilities = await this.platform.invoke(
        getCapabilities,
        undefined,
        // Un 403 de esta misma petición no debe volver a pedir las capacidades.
        new HttpContext().set(SKIP_FORBIDDEN_HANDLING, true),
      );
      if (number === this.latest) {
        this.current.set(capabilities);
      }
      return capabilities;
    } catch (error) {
      if (number === this.latest) {
        this.pending = undefined;
      }
      throw error;
    }
  }
}

function search(items: Capability[], code: string): Capability | undefined {
  for (const item of items) {
    if (item.code === code) {
      return item;
    }
    const found = search(item.children, code);
    if (found) {
      return found;
    }
  }
  return undefined;
}
