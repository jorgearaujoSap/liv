import { computed, inject, Injectable, signal } from '@angular/core';

import { CoreApi } from '../../api/core/core-api';
import { getCurrentUser } from '../../api/core/functions';
import type { CurrentUser } from '../../api/core/models';
import { CapabilityService } from '../../core/capabilities/capability.service';
import { buildMenu } from '../../core/navigation/menu-presentation';

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
  /** El menú de la persona: solo lo que el backend le permite, en su orden. */
  readonly menu = computed(() => {
    const capabilities = this.capabilities.capabilities();
    return capabilities ? buildMenu(capabilities) : [];
  });

  /**
   * Carga la persona y sus capacidades a la vez. Si una falla, falla todo y no queda la persona a medias:
   * el próximo intento vuelve a pedir.
   */
  async load(): Promise<void> {
    const [user] = await Promise.all([this.core.invoke(getCurrentUser), this.capabilities.load()]);
    this.current.set(user);
  }
}
