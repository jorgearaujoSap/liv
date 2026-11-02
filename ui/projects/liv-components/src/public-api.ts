/*
 * API pública de liv-components: lo único que las aplicaciones pueden importar de la librería.
 *
 * Cada componente nace primero en la página 02 Componentes de Figma, con el mismo nombre, y luego se
 * implementa aquí con su historia y sus pruebas (docs/arquitectura/01 §11.1).
 * Los tokens de diseño se publican como `liv-components/styles/theme.css`.
 */
export { AvatarComponent, type AvatarSize } from './lib/avatar/avatar.component';
export { BadgeComponent, type BadgeTone } from './lib/badge/badge.component';
export { IconComponent } from './lib/icon/icon.component';
export { ICON_NAMES, type IconName, type IconSize } from './lib/icon/icons';
export { LogoComponent, type LogoTone } from './lib/logo/logo.component';
export { SkeletonComponent, type SkeletonShape } from './lib/skeleton/skeleton.component';
export {
  SpinnerComponent,
  type SpinnerSize,
  type SpinnerTone,
} from './lib/spinner/spinner.component';
