import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * Encabezado de cada pantalla (Figma: `PageHeader`): ruta de navegación (p. ej. `['Administración',
 * '1.4 Vigencias fiscales']`), título y descripción opcional de máximo dos líneas. El título es el `h1`:
 * una pantalla tiene uno solo. La ruta es texto, no enlaces; la última parte es la pantalla actual.
 */
@Component({
  selector: 'liv-page-header',
  imports: [IconComponent],
  templateUrl: './page-header.component.html',
  styleUrl: './page-header.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeaderComponent {
  /** Título de la pantalla (`Title` de Figma); se llama `heading` para no chocar con el atributo `title`. */
  readonly heading = input.required<string>();
  /** Partes de la ruta de navegación, de la más general a la pantalla actual (`Breadcrumb` de Figma). */
  readonly breadcrumb = input<readonly string[]>([]);
  readonly description = input<string>();

  protected readonly descriptionText = computed(() => this.description()?.trim() || null);
}
