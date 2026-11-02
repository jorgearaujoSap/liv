import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Tono de la insignia, según el estado que comunica. */
export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';

/**
 * Etiqueta de estado corta (Figma: `Badge`). El texto, que se proyecta como contenido, siempre dice el
 * estado: el color nunca es la única señal. La propiedad `Label` de Figma es ese contenido proyectado.
 */
@Component({
  selector: 'liv-badge',
  templateUrl: './badge.component.html',
  styleUrl: './badge.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-tone]': 'tone()',
  },
})
export class BadgeComponent {
  readonly tone = input<BadgeTone>('neutral');
  readonly showDot = input(false, { transform: booleanAttribute });
}
