import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Tono del logotipo: `default` sobre fondos claros, `inverse` sobre `bg-inverse`. */
export type LogoTone = 'default' | 'inverse';

/**
 * Logotipo de LIV (Figma: `Logo`): monograma y nombre del producto. Sin el nombre, el monograma sirve
 * como icono de la aplicación. Provisional hasta tener la identidad institucional.
 */
@Component({
  selector: 'liv-logo',
  templateUrl: './logo.component.html',
  styleUrl: './logo.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'img',
    '[attr.aria-label]': 'label()',
    '[attr.data-tone]': 'tone()',
  },
})
export class LogoComponent {
  readonly tone = input<LogoTone>('default');
  readonly showWordmark = input(true, { transform: booleanAttribute });
  /** Texto alternativo del logotipo completo. */
  readonly label = input('LIV, impuesto vehicular');
}
