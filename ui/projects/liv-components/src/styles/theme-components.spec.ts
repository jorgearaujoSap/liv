import theme from './theme.css' with { loader: 'text' };
import { contrastRatio, solidColor } from '../testing/contrast';
import { cssVariable } from '../testing/css';

/*
 * Contraste de las parejas de color que usan los componentes de estructura (T05). axe no calcula el
 * contraste en jsdom (`testing/axe.ts`), así que se mide aquí con los valores de `theme.css`.
 */
const color = (name: string) => solidColor(cssVariable(theme, `--color-${name}`));

describe('theme.css · contraste de las parejas de los componentes de estructura', () => {
  it.each<[string, string, string]>([
    ['ruta de PageHeader y etiqueta de ResultState', 'text-muted', 'bg-canvas'],
    ['etiqueta de sección y pie de SideNav', 'text-muted', 'bg-surface'],
    ['texto de NavItem Default y descripciones', 'text-secondary', 'bg-surface'],
    ['texto de NavItem en hover', 'text-secondary', 'bg-subtle'],
    ['título de grupo de NavItem en hover', 'text-primary', 'bg-subtle'],
    ['texto de NavItem Active', 'action-primary', 'action-primary-subtle'],
    ['icono de ModuleCard', 'action-primary', 'action-primary-subtle'],
    ['icono de Dialog', 'status-warning', 'status-warning-subtle'],
    ['icono de ResultState Danger', 'status-danger', 'status-danger-subtle'],
    ['icono de ResultState Warning', 'status-warning', 'status-warning-subtle'],
    ['icono de ResultState Info', 'status-info', 'status-info-subtle'],
  ])('%s: %s sobre %s llega a 4,5:1 (WCAG 1.4.3)', (_, foreground, background) => {
    expect(contrastRatio(color(foreground), color(background))).toBeGreaterThanOrEqual(4.5);
  });

  it('el icono de ModuleCard deshabilitada (text/muted sobre bg/subtle) llega a 3:1 (WCAG 1.4.11)', () => {
    // Es un icono, no texto: 3:1. text/muted sobre bg/subtle no se usa para texto.
    expect(contrastRatio(color('text-muted'), color('bg-subtle'))).toBeGreaterThanOrEqual(3);
  });

  it('el anillo de foco llega a 3:1 sobre el fondo de NavItem Active (action/primary-subtle)', () => {
    expect(
      contrastRatio(color('border-focus'), color('action-primary-subtle')),
    ).toBeGreaterThanOrEqual(3);
  });
});
