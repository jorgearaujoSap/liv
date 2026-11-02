import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Etiqueta de un grupo de opciones del menú (Figma: `Section label` dentro de `SideNav`), p. ej.
 * "Módulos". El texto se proyecta como contenido y se muestra en mayúsculas.
 */
@Component({
  selector: 'liv-nav-section',
  template: '<ng-content />',
  styleUrl: './nav-section.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavSectionComponent {}
