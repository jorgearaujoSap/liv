import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Forma del marcador: línea de texto, círculo (avatar) o bloque (tarjeta, imagen). */
export type SkeletonShape = 'text' | 'circle' | 'block';

/**
 * Marcador de contenido mientras carga (Figma: `Skeleton`). Es decorativo: el contenedor que agrupa los
 * marcadores lleva `aria-busy="true"`. Con `prefers-reduced-motion` no pulsa. Sin `width` ni `height`
 * usa las medidas del diseño; se redimensiona según el contenido real que reemplaza.
 */
@Component({
  selector: 'liv-skeleton',
  template: '',
  styleUrl: './skeleton.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[attr.data-shape]': 'shape()',
    '[style.width]': 'width()',
    '[style.height]': 'height()',
  },
})
export class SkeletonComponent {
  readonly shape = input<SkeletonShape>('text');
  /** Ancho como longitud CSS (por ejemplo `50%` o `12rem`). */
  readonly width = input<string>();
  /** Alto como longitud CSS. */
  readonly height = input<string>();
}
