import theme from './theme.css' with { loader: 'text' };
import { contrastRatio, shadowLayers, solidColor, visibleColor } from '../testing/contrast';
import { cssVariable } from '../testing/css';

/*
 * Contraste de los tokens que deben cumplir WCAG 2.1 AA. axe no calcula el contraste en jsdom, así que se
 * comprueba aquí con los valores de `theme.css`. Además de bg/surface, se miden los otros fondos claros
 * donde se colocan los componentes (bg/canvas y bg/subtle).
 */
const lightBackgrounds = ['--color-bg-surface', '--color-bg-canvas', '--color-bg-subtle'];

/** Capas de focus/ring: la separación (encima) y el anillo (debajo, más ancho). */
function focusRing() {
  const [separation, ring, ...rest] = shadowLayers(cssVariable(theme, '--shadow-focus-ring'));
  expect(rest).toEqual([]);
  return { separation, ring };
}

function contrastOn(color: string, backgroundVar: string): number {
  const background = solidColor(cssVariable(theme, backgroundVar));
  return contrastRatio(visibleColor(color, background), background);
}

describe('theme.css · contraste', () => {
  it('se lee como texto (import con loader text del compilador de Angular)', () => {
    expect(typeof theme).toBe('string');
    expect(theme).toContain('@theme');
  });

  describe.each(lightBackgrounds)('sobre %s', (backgroundVar) => {
    it('border/strong llega a 3:1 (WCAG 1.4.11)', () => {
      expect(
        contrastOn(cssVariable(theme, '--color-border-strong'), backgroundVar),
      ).toBeGreaterThanOrEqual(3);
    });

    it('el anillo de focus/ring llega a 3:1 (WCAG 1.4.11)', () => {
      expect(contrastOn(focusRing().ring.color, backgroundVar)).toBeGreaterThanOrEqual(3);
    });
  });

  it('focus/ring separa el anillo del componente con bg/surface, con 3:1 entre ambos (WCAG 1.4.11)', () => {
    // Sin separación, el anillo queda pegado al relleno de Button Primary o al borde de Error.
    const { separation, ring } = focusRing();
    expect(separation.color).toBe(cssVariable(theme, '--color-bg-surface'));
    expect(contrastOn(ring.color, '--color-bg-surface')).toBeGreaterThanOrEqual(3);
  });

  it('focus/ring es un contorno nítido: separación visible y al menos 2 px de anillo por fuera (Figma)', () => {
    // Si la separación igualara o superara al anillo, lo taparía: el foco no se vería.
    const { separation, ring } = focusRing();
    for (const layer of [separation, ring]) {
      expect([layer.offsetX, layer.offsetY, layer.blur]).toEqual([0, 0, 0]);
    }
    expect(separation.spread).toBeGreaterThan(0);
    expect(ring.spread - separation.spread).toBeGreaterThanOrEqual(2);
  });

  it('text/muted, el token del placeholder, llega a 4,5:1 sobre bg/surface (WCAG 1.4.3)', () => {
    expect(
      contrastOn(cssVariable(theme, '--color-text-muted'), '--color-bg-surface'),
    ).toBeGreaterThanOrEqual(4.5);
  });
});
