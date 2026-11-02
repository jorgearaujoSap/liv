import theme from './theme.css' with { loader: 'text' };
import { contrastRatio, cssVariable, visibleColor } from '../testing/contrast';

/**
 * Contraste de los tokens que deben cumplir WCAG 2.1 AA sobre los fondos claros donde se usan los
 * componentes (tarea T04a). axe no calcula el contraste en jsdom, así que se comprueba aquí con los
 * valores de `theme.css`.
 */
const backgrounds = ['--color-bg-surface', '--color-bg-canvas', '--color-bg-subtle'];

function contrastOn(color: string, background: string): number {
  const bg = cssVariable(theme, background);
  return contrastRatio(visibleColor(color, bg), visibleColor(bg, bg));
}

describe('theme.css · contraste', () => {
  describe.each(backgrounds)('sobre %s', (background) => {
    it('border/strong llega a 3:1 (WCAG 1.4.11)', () => {
      const color = cssVariable(theme, '--color-border-strong');
      expect(contrastOn(color, background)).toBeGreaterThanOrEqual(3);
    });

    it('el color del anillo focus/ring llega a 3:1 (WCAG 1.4.11)', () => {
      const shadow = cssVariable(theme, '--shadow-focus-ring');
      const color = /(#[0-9a-f]{6}|rgb\([^)]*\))\s*$/i.exec(shadow)?.[1] ?? '';
      expect(contrastOn(color, background)).toBeGreaterThanOrEqual(3);
    });
  });

  it('text/muted, el token del placeholder, llega a 4,5:1 sobre bg/surface (WCAG 1.4.3)', () => {
    const color = cssVariable(theme, '--color-text-muted');
    expect(contrastOn(color, '--color-bg-surface')).toBeGreaterThanOrEqual(4.5);
  });
});
