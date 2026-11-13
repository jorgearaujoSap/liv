import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

let nextId = 0;

/**
 * Superficie con borde que agrupa un bloque de una pantalla (Figma: `Panel`, nodo 64:631): título,
 * descripción opcional y el contenido, que se proyecta. Es una región (`section`) cuyo nombre accesible
 * es su título.
 *
 * El título es un `h2` por omisión; `headingLevel` lo pasa a `h3` cuando el panel está dentro de otro
 * bloque con su propio `h2`. El nivel no cambia el aspecto (en el diseño es `heading/h3`). Figma no tiene
 * espacios de contenido: allí se compone desacoplando la instancia.
 *
 * Cada panel es un punto de referencia (`region`) de la página: dos paneles con el mismo título no se
 * distinguen para un lector de pantalla, así que los títulos de una página deben ser distintos.
 */
@Component({
  selector: 'liv-panel',
  templateUrl: './panel.component.html',
  styleUrl: './panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelComponent {
  /** Título del panel (`Title` de Figma). */
  readonly heading = input.required<string>();
  /** Texto bajo el título (`Description` y `Show description` de Figma); vacío, no se muestra. */
  readonly description = input<string>();
  /** Nivel del título en el documento: `h2` o `h3`. */
  readonly headingLevel = input<2 | 3>(2);

  private readonly uid = nextId++;
  protected readonly headingId = `liv-panel-heading-${this.uid}`;
  protected readonly descriptionId = `liv-panel-description-${this.uid}`;
  protected readonly descriptionText = computed(() => this.description()?.trim() || null);
}
