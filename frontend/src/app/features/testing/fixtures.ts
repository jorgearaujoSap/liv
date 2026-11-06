import { signal } from '@angular/core';

import type { CurrentUser } from '../../api/core/models';
import type { Capabilities, Capability } from '../../api/platform/models';
import { buildMenu, type MenuModule } from '../../core/navigation/menu-presentation';

/**
 * Datos de prueba de las pantallas (solo los usan los `*.spec.ts`; no entra a la aplicación). Son ficticios:
 * ningún dato de contribuyentes ni de funcionarios reales.
 */
export const USER: CurrentUser = {
  id: '5f0d1c52-7a5e-4f0e-9a41-0c6f8a3b2d10',
  fullName: 'Laura Martínez',
  email: 'laura.martinez@example.org',
  administration: { id: 'b41a0d4e-0000-4000-8000-000000000001', name: 'Municipio de Ipiales' },
  roles: ['ADMIN_SISTEMA'],
  permissions: [],
};

export const leaf = (code: string, status: Capability['status'] = 'COMING_SOON'): Capability => ({
  code,
  status,
  children: [],
});

/** Los nueve módulos del menú, todos sin construir, y el 1 con algunas subopciones. */
export const ALL_MODULES: Capabilities = {
  items: [
    {
      code: '1',
      status: 'COMING_SOON',
      children: [leaf('1.1'), leaf('1.2'), leaf('1.4')],
    },
    ...['2', '3', '4', '5', '6', '7', '8', '9'].map((code) => leaf(code)),
  ],
};

/** Lo que `WorkspaceService` ofrece a las pantallas, con la carga que la prueba controla. */
export class FakeWorkspace {
  readonly user = signal<CurrentUser | undefined>(USER);
  readonly menu = signal<MenuModule[]>(buildMenu(ALL_MODULES));
  loading: Promise<void> = Promise.resolve();

  load(): Promise<void> {
    return this.loading;
  }
}
