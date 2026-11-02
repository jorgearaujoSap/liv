import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

/** Gravedad del resultado: `danger` (error o sin permiso), `warning` o `info` (p. ej. "Próximamente"). */
export type ResultStateTone = 'danger' | 'warning' | 'info';

/**
 * Página o bloque de resultado (Figma: `ResultState`): sin permiso (403), error de carga, sesión
 * terminada o vacío. Icono grande, título que dice qué pasó, descripción que dice qué hacer y una o dos
 * acciones, que se proyectan como contenido (`Primary action` y `Secondary action` de Figma, con
 * `liv-button`). El icono es decorativo: el título dice qué pasó, porque el color no es la única señal.
 *
 * `headingLevel` es el nivel del título: 1 si el resultado es toda la página, 2 si va dentro de otra
 * (la pantalla debe tener un solo `h1`).
 */
@Component({
  selector: 'liv-result-state',
  imports: [IconComponent],
  templateUrl: './result-state.component.html',
  styleUrl: './result-state.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-tone]': 'tone()',
  },
})
export class ResultStateComponent {
  /** Gravedad: colorea el círculo del icono; por defecto, `danger`. */
  readonly tone = input<ResultStateTone>('danger');
  readonly icon = input.required<IconName>();
  /** Etiqueta sobre el título, p. ej. "Error 403" (`Show eyebrow` y `Eyebrow` de Figma). */
  readonly eyebrow = input<string>();
  /** Título (`Title` de Figma); se llama `heading` para no chocar con el atributo `title`. */
  readonly heading = input.required<string>();
  /** Nivel del encabezado del título (`h1`, `h2` o `h3`); por defecto, 2. */
  readonly headingLevel = input<1 | 2 | 3>(2);
  readonly description = input<string>();

  protected readonly eyebrowText = computed(() => this.eyebrow()?.trim() || null);
  protected readonly descriptionText = computed(() => this.description()?.trim() || null);
}
