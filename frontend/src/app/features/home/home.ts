import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ModuleCardComponent, PageHeaderComponent } from 'liv-components';

import { WorkspaceService } from '../shell/workspace.service';

/**
 * Inicio (001-02, 001-15): saluda y muestra una tarjeta por cada módulo que el backend permite a la persona.
 * Los módulos aún sin construir salen deshabilitados, con la insignia «Próximamente». Diseño: Figma
 * LIV-Diseño, `03 Pantallas`, «Inicio» (nodo 26:793).
 */
@Component({
  selector: 'app-home',
  imports: [PageHeaderComponent, ModuleCardComponent],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly router = inject(Router);
  private readonly workspace = inject(WorkspaceService);

  protected readonly modules = this.workspace.menu;

  protected readonly greeting = computed(() => {
    const firstName = this.workspace.user()?.fullName.trim().split(/\s+/)[0];
    return firstName ? `Hola, ${firstName}` : 'Hola';
  });

  protected readonly description = computed(
    () =>
      `Estas son las opciones disponibles para tu rol en ${this.workspace.administrationName()}. Los módulos se habilitan a medida que se publican.`,
  );

  protected open(href: string): void {
    void this.router.navigateByUrl(href);
  }
}
