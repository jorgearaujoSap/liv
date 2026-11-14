/*
 * API pública de liv-components: lo único que las aplicaciones pueden importar de la librería.
 *
 * Cada componente nace primero en la página 02 Componentes de Figma, con el mismo nombre, y luego se
 * implementa aquí con su historia y sus pruebas (docs/arquitectura/01 §11.1).
 * Los tokens de diseño se publican como `liv-components/styles/theme.css`.
 */
export { AlertComponent, type AlertStatus } from './lib/alert/alert.component';
export { AppHeaderComponent } from './lib/app-header/app-header.component';
export { AvatarComponent, type AvatarSize } from './lib/avatar/avatar.component';
export { BadgeComponent, type BadgeTone } from './lib/badge/badge.component';
export {
  ButtonComponent,
  type ButtonReasonAlign,
  type ButtonSize,
  type ButtonType,
  type ButtonVariant,
} from './lib/button/button.component';
export {
  ChecklistItemComponent,
  type ChecklistItemStatus,
} from './lib/checklist-item/checklist-item.component';
export { CodeInputComponent } from './lib/code-input/code-input.component';
export {
  DescriptionItemComponent,
  type DescriptionItemType,
} from './lib/description-item/description-item.component';
export { DialogComponent } from './lib/dialog/dialog.component';
export {
  IconButtonComponent,
  type IconButtonSize,
  type IconButtonVariant,
} from './lib/icon-button/icon-button.component';
export { IconComponent } from './lib/icon/icon.component';
export { ICON_NAMES, type IconName, type IconSize } from './lib/icon/icons';
export { LogoComponent, type LogoTone } from './lib/logo/logo.component';
export { ModuleCardComponent } from './lib/module-card/module-card.component';
export { NavItemComponent, type NavItemType } from './lib/nav-item/nav-item.component';
export { NavSectionComponent } from './lib/side-nav/nav-section.component';
export { PageHeaderComponent } from './lib/page-header/page-header.component';
export { PanelComponent } from './lib/panel/panel.component';
export { RadioGroupComponent } from './lib/radio-group/radio-group.component';
export { RadioOptionComponent } from './lib/radio-group/radio-option.component';
export {
  ResultStateComponent,
  type ResultStateTone,
} from './lib/result-state/result-state.component';
export { SideNavComponent } from './lib/side-nav/side-nav.component';
export { SkeletonComponent, type SkeletonShape } from './lib/skeleton/skeleton.component';
export {
  SpinnerComponent,
  type SpinnerSize,
  type SpinnerTone,
} from './lib/spinner/spinner.component';
export { TableCellComponent, type TableCellType } from './lib/table/table-cell.component';
export { TableRowComponent, type TableRowType } from './lib/table/table-row.component';
export { TableComponent } from './lib/table/table.component';
export { TextFieldComponent, type TextFieldType } from './lib/text-field/text-field.component';
export { formatBusinessDate, type BusinessDateFormat } from './lib/shared/business-date';
