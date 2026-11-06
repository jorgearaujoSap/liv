import { HttpContext } from '@angular/common/http';
import { computed, effect, inject, Injectable, signal, untracked } from '@angular/core';

import { CoreApi } from '../../api/core/core-api';
import { getCurrentUser } from '../../api/core/functions';
import type { CurrentUser } from '../../api/core/models';
import type { Capabilities } from '../../api/platform/models';
import { CapabilityService } from '../../core/capabilities/capability.service';
import { SKIP_FORBIDDEN_HANDLING } from '../../core/http/api-context';
import { withTimeout } from '../../core/http/with-timeout';
import { buildMenu } from '../../core/navigation/menu-presentation';

/** Lo que se espera al servidor al armar la estructura general antes de darlo por caído. */
const LOAD_TIMEOUT_MS = 10_000;

/**
 * Lo que las pantallas saben de la persona que trabaja: quién es, en qué administración y qué menú ve
 * (001-02, 001-15). Todo viene del servidor (`/api/v1/me` y `/api/v1/me/capabilities`), nunca del navegador.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly core = inject(CoreApi);
  private readonly capabilities = inject(CapabilityService);

  private readonly current = signal<CurrentUser | undefined>(undefined);
  /** La persona de la sesión; `undefined` mientras no se haya cargado. */
  readonly user = this.current.asReadonly();
  /** Su administración, o «tu entidad» mientras no se sepa. */
  readonly administrationName = computed(() => this.current()?.administration.name ?? 'tu entidad');
  /** El menú de la persona: solo lo que el backend le permite, en su orden. */
  readonly menu = computed(() => {
    const capabilities = this.capabilities.capabilities();
    return capabilities ? buildMenu(capabilities) : [];
  });

  /** Las capacidades con las que se cargó la persona: si cambian (p. ej. tras un 403), la persona también. */
  private seen: Capabilities | undefined;
  /** Número del último refresco pedido: la respuesta de uno anterior no pisa la de uno más nuevo. */
  private refreshes = 0;

  constructor() {
    effect(() => {
      const capabilities = this.capabilities.capabilities();
      untracked(() => {
        if (capabilities && this.current() && capabilities !== this.seen) {
          this.seen = capabilities;
          void this.refresh();
        }
      });
    });
  }

  /**
   * Carga la persona y sus capacidades a la vez, con tiempo límite. Si una falla, falla todo y no queda la
   * persona a medias: el próximo intento vuelve a pedir.
   */
  async load(): Promise<void> {
    const [user] = await withTimeout(
      Promise.all([this.requestUser(), this.capabilities.load()]),
      LOAD_TIMEOUT_MS,
    );
    this.current.set(user);
    this.seen = this.capabilities.capabilities();
  }

  /**
   * Vuelve a pedir quién es la persona: el rol o la administración pudieron cambiar con la sesión abierta, y
   * la cabecera no puede quedarse con los de antes. Si falla, se conserva la anterior.
   */
  private async refresh(): Promise<void> {
    const number = ++this.refreshes;
    try {
      const user = await this.requestUser();
      if (number === this.refreshes) {
        this.current.set(user);
      }
    } catch {
      // Se conserva la persona anterior; el próximo cambio de capacidades vuelve a intentarlo.
    }
  }

  /**
   * Un 403 al pedir quién es la persona no es «sin permiso a una opción»: no hay con qué armar la estructura
   * general, así que no dispara el manejo del 403 del interceptor.
   */
  private requestUser(): Promise<CurrentUser> {
    return this.core.invoke(
      getCurrentUser,
      undefined,
      new HttpContext().set(SKIP_FORBIDDEN_HANDLING, true),
    );
  }
}
