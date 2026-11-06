import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonComponent, ResultStateComponent } from 'liv-components';

/**
 * Próximamente (001-15): la opción existe en el menú pero aún no está construida. Diseño: Figma
 * LIV-Diseño, `03 Pantallas`, «Próximamente» (nodo 29:1689). Va dentro de la estructura general.
 */
@Component({
  selector: 'app-coming-soon',
  imports: [ResultStateComponent, ButtonComponent],
  templateUrl: './coming-soon.html',
  styleUrl: './result-page.css',
})
export class ComingSoon {
  private readonly router = inject(Router);

  protected goHome(): void {
    void this.router.navigateByUrl('/');
  }
}
