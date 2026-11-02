type Rgb = [number, number, number];

/**
 * Valor de una variable CSS declarada en el texto de una hoja de estilos (p. ej. `theme.css`), o error si
 * no existe: la prueba debe fallar si el token desaparece, no pasar en silencio.
 */
export function cssVariable(css: string, name: string): string {
  const match = new RegExp(`${name}\\s*:\\s*([^;]+);`).exec(css);
  if (!match) {
    throw new Error(`No existe la variable ${name}`);
  }
  return match[1].trim();
}

/**
 * Color sólido que se ve al pintar `color` sobre `background`. Acepta `#rrggbb` y `rgb(r g b / a)`; con
 * transparencia, mezcla el color con el fondo, como lo hace el navegador.
 */
export function visibleColor(color: string, background: string): Rgb {
  const hex = /^#([0-9a-f]{6})$/i.exec(color);
  if (hex) {
    return [0, 2, 4].map((i) => parseInt(hex[1].slice(i, i + 2), 16)) as Rgb;
  }
  const rgb = /^rgb\(\s*(\d+)\s+(\d+)\s+(\d+)\s*(?:\/\s*([\d.]+))?\s*\)$/i.exec(color);
  if (!rgb) {
    throw new Error(`Color no admitido: ${color}`);
  }
  const alpha = rgb[4] === undefined ? 1 : Number(rgb[4]);
  const under = visibleColor(background, background);
  return [1, 2, 3].map((i, k) => Number(rgb[i]) * alpha + under[k] * (1 - alpha)) as Rgb;
}

/** Contraste WCAG 2.1 entre dos colores sólidos, de 1 a 21. */
export function contrastRatio(a: Rgb, b: Rgb): number {
  const luminance = (rgb: Rgb) => {
    const [r, g, bl] = rgb.map((channel) => {
      const c = channel / 255;
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}
