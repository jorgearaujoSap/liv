/** Formato de una fecha de negocio: larga («1 de enero de 2026») o corta (`dd/mm/aaaa`). */
export type BusinessDateFormat = 'long' | 'short';

const BUSINESS_DATE = /^([1-9]\d{3})-(\d{2})-(\d{2})$/;

/** Año, mes y día de un `AAAA-MM-DD`, o `null` si no es una fecha que exista en el calendario. */
function parseBusinessDate(value: string): { year: number; month: number; day: number } | null {
  const parts = BUSINESS_DATE.exec(value);
  if (!parts) return null;
  const [year, month, day] = [Number(parts[1]), Number(parts[2]), Number(parts[3])];
  const date = new Date(Date.UTC(year, month - 1, day));
  const exists =
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
  return exists ? { year, month, day } : null;
}

/**
 * Los meses van escritos aquí, en español, y no salen de `Intl`: un motor sin los datos de `es-CO` (un ICU
 * reducido) caería en silencio en inglés («January 1, 2026»). Así la fecha es la misma en cualquier
 * equipo y sin construir ninguna fecha con zona.
 */
const MONTHS = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
] as const;

const twoDigits = (n: number) => String(n).padStart(2, '0');

/**
 * Formatea una fecha de negocio (`AAAA-MM-DD`, como llega del contrato) **sin convertir de zona**: una
 * fecha de negocio es un día del calendario, no un instante. `new Date('2026-01-01')` la tomaría como las
 * 00:00 UTC y en Bogotá (UTC−5) mostraría el 31 de diciembre de 2025.
 *
 * - `long` (por omisión): «1 de enero de 2026» (español de Colombia), para fechas sueltas (H2-13).
 * - `short`: `01/01/2026` (`dd/mm/aaaa`), para tablas de consulta y exportaciones.
 *
 * Devuelve `null` si el valor está ausente, no es un texto o no es una fecha `AAAA-MM-DD` que exista (un
 * `2026-02-30`, una fecha con hora o con otro orden): la pantalla no debe mostrar una fecha inventada.
 */
export function formatBusinessDate(
  value: string | null | undefined,
  format: BusinessDateFormat = 'long',
): string | null {
  const date = typeof value === 'string' ? parseBusinessDate(value) : null;
  if (!date) return null;
  if (format === 'short') return `${twoDigits(date.day)}/${twoDigits(date.month)}/${date.year}`;
  return `${date.day} de ${MONTHS[date.month - 1]} de ${date.year}`;
}
