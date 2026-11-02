export type Rgb = [number, number, number];

/** Color opaco de `#rrggbb`, o error si el formato no se admite o el color tiene transparencia. */
export function solidColor(color: string): Rgb {
  const hex = /^#([0-9a-f]{6})$/i.exec(color.trim());
  if (!hex) {
    throw new Error(`Se esperaba un color opaco #rrggbb: ${color}`);
  }
  return [0, 2, 4].map((i) => parseInt(hex[1].slice(i, i + 2), 16)) as Rgb;
}

/**
 * Color que se ve al pintar `color` sobre un fondo opaco. Acepta `#rrggbb` y `rgb(r g b / a)`; con
 * transparencia, mezcla el color con el fondo, como lo hace el navegador.
 */
export function visibleColor(color: string, background: Rgb): Rgb {
  const rgb = /^rgb\(\s*(\d+)\s+(\d+)\s+(\d+)\s*(?:\/\s*([\d.]+))?\s*\)$/i.exec(color.trim());
  if (!rgb) {
    return solidColor(color);
  }
  const alpha = Math.min(1, rgb[4] === undefined ? 1 : Number(rgb[4]));
  return [1, 2, 3].map(
    (group, i) => Number(rgb[group]) * alpha + background[i] * (1 - alpha),
  ) as Rgb;
}

/**
 * Colores de cada capa de una sombra (`0 0 0 2px #fff, 0 0 0 4px #2563eb`), en el orden declarado: la
 * primera se pinta encima. Falla si alguna capa no termina en un color.
 */
export function shadowColors(shadow: string): string[] {
  return shadow.split(/,(?![^(]*\))/).map((layer) => {
    const color = /(#[0-9a-f]{6}|rgb\([^)]*\))\s*$/i.exec(layer.trim())?.[1];
    if (!color) {
      throw new Error(`La capa de sombra no termina en un color: ${layer}`);
    }
    return color;
  });
}

/** Contraste entre dos colores opacos, de 1 a 21 (WCAG 2.1, definición de «contrast ratio»). */
export function contrastRatio(a: Rgb, b: Rgb): number {
  const [lighter, darker] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x);
  return (lighter + 0.05) / (darker + 0.05);
}

/** Luminancia relativa de sRGB (WCAG 2.1, definición de «relative luminance»). */
function relativeLuminance(rgb: Rgb): number {
  const [red, green, blue] = rgb.map((channel) => {
    const c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}
