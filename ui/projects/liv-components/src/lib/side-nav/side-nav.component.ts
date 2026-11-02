import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LogoComponent } from '../logo/logo.component';

const DEFAULT_LABEL = 'Menú principal';

/**
 * Menú lateral (Figma: `SideNav`): logotipo arriba, las opciones proyectadas como contenido
 * (`liv-nav-item` y `liv-nav-section`) y un pie opcional, p. ej. la versión. Es la región de navegación
 * principal (`nav` con nombre accesible). Ocupa el alto de su contenedor y 272 px de ancho; el menú se
 * desplaza si no cabe. El comportamiento en pantallas angostas no está diseñado todavía.
 */
@Component({
  selector: 'liv-side-nav',
  imports: [LogoComponent],
  templateUrl: './side-nav.component.html',
  styleUrl: './side-nav.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SideNavComponent {
  /** Nombre accesible de la región; por defecto, "Menú principal". */
  readonly label = input<string>();
  /** Texto del pie (`Footer` de Figma), p. ej. "LIV · versión 0.1.0". */
  readonly footer = input<string>();

  protected readonly accessibleName = computed(() => this.label()?.trim() || DEFAULT_LABEL);
  protected readonly footerText = computed(() => this.footer()?.trim() || null);
}
