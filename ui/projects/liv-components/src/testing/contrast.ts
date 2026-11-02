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

export interface ShadowLayer {
  offsetX: number;
  offsetY: number;
  blur: number;
  spread: number;
  color: string;
}

/**
 * Capas de una sombra exterior (`0 0 0 2px #ffffff, 0 0 0 4px #2563eb`), en el orden declarado: la primera
 * se pinta encima. Medidas en px. Falla si una capa no es `x y [desenfoque [extensión]] color`, para no
 * medir a medias una sombra que no sabe leer (p. ej. `inset` o un color en otra posición).
 */
export function shadowLayers(shadow: string): ShadowLayer[] {
  return shadow.split(/,(?![^(]*\))/).map((layer) => {
    const match = /^(.*?)\s*(#[0-9a-f]{6}|rgb\([^)]*\))$/i.exec(layer.trim());
    const lengths = (match?.[1] ?? '')
      .split(/\s+/)
      .map((length) => /^(-?[\d.]+)(px)?$/.exec(length));
    if (!match || lengths.length < 2 || lengths.length > 4 || lengths.some((length) => !length)) {
      throw new Error(`Capa de sombra no admitida: ${layer}`);
    }
    const [offsetX, offsetY, blur = 0, spread = 0] = lengths.map((length) => Number(length?.[1]));
    return { offsetX, offsetY, blur, spread, color: match[2] };
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
