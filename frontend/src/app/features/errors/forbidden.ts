import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonComponent, ResultStateComponent } from 'liv-components';

import { WorkspaceService } from '../shell/workspace.service';

/**
 * Sin permiso, 403 (001-16): la persona abrió por URL una opción que su rol no incluye. Diseño: Figma
 * LIV-Diseño, `03 Pantallas`, «Sin permiso» (nodo 26:1454). Va dentro de la estructura general.
 */
@Component({
  selector: 'app-forbidden',
  imports: [ResultStateComponent, ButtonComponent],
  templateUrl: './forbidden.html',
  styleUrl: './result-page.css',
})
export class Forbidden {
  private readonly router = inject(Router);
  private readonly workspace = inject(WorkspaceService);

  protected readonly description = computed(() => {
    const administration = this.workspace.user()?.administration.name ?? 'tu entidad';
    return `Tu rol no incluye esta opción en ${administration}. Si necesitas acceso, solicítalo al administrador de tu entidad.`;
  });

  protected goHome(): void {
    void this.router.navigateByUrl('/');
  }
}
