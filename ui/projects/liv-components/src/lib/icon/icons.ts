/*
 * Iconos de Lucide v1.66.0 (https://lucide.dev, licencia ISC; los derivados de Feather, MIT). Los avisos
 * de licencia están en THIRD_PARTY_NOTICES.md. Son los mismos 38 de la página 02 Componentes de Figma
 * (`Icon/<nombre>`): lienzo de 24 px, trazo de 2 px, extremos y uniones redondeados.
 * Para agregar uno, primero se agrega en Figma.
 */

/** Una forma SVG de un icono; solo se usan los atributos que Lucide emplea en estos iconos. */
export interface IconShape {
  readonly tag: 'path' | 'circle' | 'rect' | 'line';
  readonly d?: string;
  readonly cx?: string;
  readonly cy?: string;
  readonly r?: string;
  readonly x?: string;
  readonly y?: string;
  readonly width?: string;
  readonly height?: string;
  readonly rx?: string;
  readonly ry?: string;
  readonly x1?: string;
  readonly y1?: string;
  readonly x2?: string;
  readonly y2?: string;
  readonly fill?: string;
}

const ICONS = {
  'arrow-right': [
    { tag: 'path', d: 'M5 12h14' },
    { tag: 'path', d: 'm12 5 7 7-7 7' },
  ],
  calculator: [
    { tag: 'rect', width: '16', height: '20', x: '4', y: '2', rx: '2' },
    { tag: 'line', x1: '8', x2: '16', y1: '6', y2: '6' },
    { tag: 'line', x1: '16', x2: '16', y1: '14', y2: '18' },
    { tag: 'path', d: 'M16 10h.01' },
    { tag: 'path', d: 'M12 10h.01' },
    { tag: 'path', d: 'M8 10h.01' },
    { tag: 'path', d: 'M12 14h.01' },
    { tag: 'path', d: 'M8 14h.01' },
    { tag: 'path', d: 'M12 18h.01' },
    { tag: 'path', d: 'M8 18h.01' },
  ],
  'calendar-days': [
    { tag: 'path', d: 'M8 2v3' },
    { tag: 'path', d: 'M16 2v3' },
    { tag: 'rect', x: '3', y: '3', width: '18', height: '18', rx: '2' },
    { tag: 'path', d: 'M3 9h18' },
    { tag: 'path', d: 'M8 13h.01' },
    { tag: 'path', d: 'M12 13h.01' },
    { tag: 'path', d: 'M16 13h.01' },
    { tag: 'path', d: 'M8 17h.01' },
    { tag: 'path', d: 'M12 17h.01' },
    { tag: 'path', d: 'M16 17h.01' },
  ],
  car: [
    {
      tag: 'path',
      d: 'M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2',
    },
    { tag: 'circle', cx: '7', cy: '17', r: '2' },
    { tag: 'path', d: 'M9 17h6' },
    { tag: 'circle', cx: '17', cy: '17', r: '2' },
  ],
  'chevron-down': [{ tag: 'path', d: 'm6 9 6 6 6-6' }],
  'chevron-right': [{ tag: 'path', d: 'm9 18 6-6-6-6' }],
  'circle-alert': [
    { tag: 'circle', cx: '12', cy: '12', r: '10' },
    { tag: 'line', x1: '12', x2: '12', y1: '8', y2: '12' },
    { tag: 'line', x1: '12', x2: '12.01', y1: '16', y2: '16' },
  ],
  'circle-check': [
    { tag: 'circle', cx: '12', cy: '12', r: '10' },
    { tag: 'path', d: 'm16 9-5.5 5.5L8 12' },
  ],
  'circle-help': [
    { tag: 'circle', cx: '12', cy: '12', r: '10' },
    { tag: 'path', d: 'M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3' },
    { tag: 'path', d: 'M12 17h.01' },
  ],
  'cloud-off': [
    { tag: 'path', d: 'M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057' },
    { tag: 'path', d: 'M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78' },
    { tag: 'path', d: 'm2 2 20 20' },
  ],
  'eye-off': [
    {
      tag: 'path',
      d: 'M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49',
    },
    { tag: 'path', d: 'M14.084 14.158a3 3 0 0 1-4.242-4.242' },
    {
      tag: 'path',
      d: 'M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143',
    },
    { tag: 'path', d: 'm2 2 20 20' },
  ],
  eye: [
    {
      tag: 'path',
      d: 'M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0',
    },
    { tag: 'circle', cx: '12', cy: '12', r: '3' },
  ],
  'file-search': [
    {
      tag: 'path',
      d: 'M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z',
    },
    { tag: 'path', d: 'M14 2v5a1 1 0 0 0 1 1h5' },
    { tag: 'circle', cx: '11.5', cy: '14.5', r: '2.5' },
    { tag: 'path', d: 'M13.3 16.3 15 18' },
  ],
  'file-text': [
    {
      tag: 'path',
      d: 'M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z',
    },
    { tag: 'path', d: 'M14 2v5a1 1 0 0 0 1 1h5' },
    { tag: 'path', d: 'M10 9H8' },
    { tag: 'path', d: 'M16 13H8' },
    { tag: 'path', d: 'M16 17H8' },
  ],
  fingerprint: [
    { tag: 'path', d: 'M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4' },
    { tag: 'path', d: 'M14 13.12c0 2.38 0 6.38-1 8.88' },
    { tag: 'path', d: 'M17.29 21.02c.12-.6.43-2.3.5-3.02' },
    { tag: 'path', d: 'M2 12a10 10 0 0 1 18-6' },
    { tag: 'path', d: 'M2 16h.01' },
    { tag: 'path', d: 'M21.8 16c.2-2 .131-5.354 0-6' },
    { tag: 'path', d: 'M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2' },
    { tag: 'path', d: 'M8.65 22c.21-.66.45-1.32.57-2' },
    { tag: 'path', d: 'M9 6.8a6 6 0 0 1 9 5.2v2' },
  ],
  'folder-open': [
    {
      tag: 'path',
      d: 'm6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2',
    },
  ],
  gavel: [
    { tag: 'path', d: 'm14 13-8.381 8.38a1 1 0 0 1-3.001-3l8.384-8.381' },
    { tag: 'path', d: 'm16 16 6-6' },
    { tag: 'path', d: 'm21.5 10.5-8-8' },
    { tag: 'path', d: 'm8 8 6-6' },
    { tag: 'path', d: 'm8.5 7.5 8 8' },
  ],
  'hand-coins': [
    { tag: 'path', d: 'M11 15h2a2 2 0 1 0 0-4h-3c-.6 0-1.1.2-1.4.6L3 17' },
    {
      tag: 'path',
      d: 'm7 21 1.6-1.4c.3-.4.8-.6 1.4-.6h4c1.1 0 2.1-.4 2.8-1.2l4.6-4.4a2 2 0 0 0-2.75-2.91l-4.2 3.9',
    },
    { tag: 'path', d: 'm2 16 6 6' },
    { tag: 'circle', cx: '16', cy: '9', r: '2.9' },
    { tag: 'circle', cx: '6', cy: '5', r: '3' },
  ],
  history: [
    { tag: 'path', d: 'M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8' },
    { tag: 'path', d: 'M3 3v5h5' },
    { tag: 'path', d: 'M12 7v5l4 2' },
  ],
  house: [
    { tag: 'path', d: 'M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8' },
    {
      tag: 'path',
      d: 'M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
    },
  ],
  info: [
    { tag: 'circle', cx: '12', cy: '12', r: '10' },
    { tag: 'path', d: 'M12 16v-4' },
    { tag: 'path', d: 'M12 8h.01' },
  ],
  'key-round': [
    {
      tag: 'path',
      d: 'M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z',
    },
    { tag: 'circle', cx: '16.5', cy: '7.5', r: '.5', fill: 'currentColor' },
  ],
  landmark: [
    { tag: 'path', d: 'M10 18v-7' },
    {
      tag: 'path',
      d: 'M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z',
    },
    { tag: 'path', d: 'M14 18v-7' },
    { tag: 'path', d: 'M18 18v-7' },
    { tag: 'path', d: 'M3 22h18' },
    { tag: 'path', d: 'M6 18v-7' },
  ],
  'lock-keyhole': [
    { tag: 'circle', cx: '12', cy: '16', r: '1' },
    { tag: 'rect', x: '3', y: '10', width: '18', height: '12', rx: '2' },
    { tag: 'path', d: 'M7 10V7a5 5 0 0 1 10 0v3' },
  ],
  lock: [
    { tag: 'rect', width: '18', height: '11', x: '3', y: '11', rx: '2', ry: '2' },
    { tag: 'path', d: 'M7 11V7a5 5 0 0 1 10 0v4' },
  ],
  'log-out': [
    { tag: 'path', d: 'm16 17 5-5-5-5' },
    { tag: 'path', d: 'M21 12H9' },
    { tag: 'path', d: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4' },
  ],
  'refresh-cw': [
    { tag: 'path', d: 'M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8' },
    { tag: 'path', d: 'M21 3v5h-5' },
    { tag: 'path', d: 'M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16' },
    { tag: 'path', d: 'M8 16H3v5' },
  ],
  'scroll-text': [
    { tag: 'path', d: 'M15 12h-5' },
    { tag: 'path', d: 'M15 8h-5' },
    { tag: 'path', d: 'M19 17V5a2 2 0 0 0-2-2H4' },
    {
      tag: 'path',
      d: 'M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3',
    },
  ],
  settings: [
    {
      tag: 'path',
      d: 'M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915',
    },
    { tag: 'circle', cx: '12', cy: '12', r: '3' },
  ],
  sheet: [
    { tag: 'rect', width: '18', height: '18', x: '3', y: '3', rx: '2', ry: '2' },
    { tag: 'line', x1: '3', x2: '21', y1: '9', y2: '9' },
    { tag: 'line', x1: '3', x2: '21', y1: '15', y2: '15' },
    { tag: 'line', x1: '9', x2: '9', y1: '9', y2: '21' },
    { tag: 'line', x1: '15', x2: '15', y1: '9', y2: '21' },
  ],
  'shield-check': [
    {
      tag: 'path',
      d: 'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
    },
    { tag: 'path', d: 'm9 12 2 2 4-4' },
  ],
  'shield-x': [
    {
      tag: 'path',
      d: 'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
    },
    { tag: 'path', d: 'm14.5 9.5-5 5' },
    { tag: 'path', d: 'm9.5 9.5 5 5' },
  ],
  shield: [
    {
      tag: 'path',
      d: 'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
    },
  ],
  smartphone: [
    { tag: 'rect', width: '14', height: '20', x: '5', y: '2', rx: '2', ry: '2' },
    { tag: 'path', d: 'M12 18h.01' },
  ],
  'triangle-alert': [
    { tag: 'path', d: 'm21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3' },
    { tag: 'path', d: 'M12 9v4' },
    { tag: 'path', d: 'M12 17h.01' },
  ],
  user: [
    { tag: 'path', d: 'M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2' },
    { tag: 'circle', cx: '12', cy: '7', r: '4' },
  ],
  users: [
    { tag: 'path', d: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2' },
    { tag: 'path', d: 'M16 3.128a4 4 0 0 1 0 7.744' },
    { tag: 'path', d: 'M22 21v-2a4 4 0 0 0-3-3.87' },
    { tag: 'circle', cx: '9', cy: '7', r: '4' },
  ],
  wallet: [
    {
      tag: 'path',
      d: 'M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1',
    },
    { tag: 'path', d: 'M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4' },
  ],
} as const satisfies Record<string, readonly IconShape[]>;

/** Nombre de un icono, igual al de Figma sin el prefijo `Icon/`. */
export type IconName = keyof typeof ICONS;

/** Tamaños en píxeles que usa el diseño. */
export type IconSize = 16 | 20 | 24;

export const ICON_NAMES = Object.keys(ICONS) as readonly IconName[];

/** Formas de un icono. */
export function iconShapes(name: IconName): readonly IconShape[] {
  return ICONS[name];
}
