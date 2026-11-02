import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Tono del indicador: `default` sobre fondos claros, `inverse` dentro de botones primarios. */
export type SpinnerTone = 'default' | 'inverse';

/** Tamaños en píxeles: 20 es el del diseño; 16 y 24 son los que escala. */
export type SpinnerSize = 16 | 20 | 24;

/**
 * Carga indeterminada (Figma: `Spinner`). Siempre dice qué se carga, con un texto visible solo para los
 * lectores de pantalla (`aria-live="polite"`). Con `prefers-reduced-motion` no gira.
 */
@Component({
  selector: 'liv-spinner',
  templateUrl: './spinner.component.html',
  styleUrl: './spinner.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'status',
    'aria-live': 'polite',
    '[attr.data-tone]': 'tone()',
  },
})
export class SpinnerComponent {
  readonly tone = input<SpinnerTone>('default');
  readonly size = input<SpinnerSize>(20);
  /** Qué se está cargando. */
  readonly label = input('Cargando');
}
