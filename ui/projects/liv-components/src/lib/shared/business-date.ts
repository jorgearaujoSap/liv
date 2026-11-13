/** Formato de una fecha de negocio: larga («1 de enero de 2026») o corta (`dd/mm/aaaa`). */
export type BusinessDateFormat = 'long' | 'short';

const BUSINESS_DATE = /^([1-9]\d{3})-(\d{2})-(\d{2})$/;

/** Mes y día como los escribe `AAAA-MM-DD`, y la fecha en UTC con la que se comprueba que existe. */
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
 * La fecha larga la da `Intl` con la zona UTC, la misma con la que se construye la fecha: así el día no
 * cambia con la zona de quien lo mira. El nombre del mes sale del español de Colombia, no del equipo.
 */
const LONG_DATE = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

/**
 * Formatea una fecha de negocio (`AAAA-MM-DD`, como llega del contrato) **sin convertir de zona**: una
 * fecha de negocio es un día del calendario, no un instante. `new Date('2026-01-01')` la tomaría como las
 * 00:00 UTC y en Bogotá (UTC−5) mostraría el 31 de diciembre de 2025.
 *
 * - `long` (por omisión): «1 de enero de 2026», para fechas sueltas (H2-13).
 * - `short`: `01/01/2026` (`dd/mm/aaaa`), para tablas de consulta y exportaciones.
 *
 * Devuelve `null` si el valor está ausente o no es una fecha `AAAA-MM-DD` que exista (un `2026-02-30`, una
 * fecha con hora o con otro orden): la pantalla no debe mostrar una fecha inventada.
 */
export function formatBusinessDate(
  value: string | null | undefined,
  format: BusinessDateFormat = 'long',
): string | null {
  const date = value == null ? null : parseBusinessDate(value);
  if (!date) return null;
  if (format === 'short') {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(date.day)}/${pad(date.month)}/${date.year}`;
  }
  return LONG_DATE.format(new Date(Date.UTC(date.year, date.month - 1, date.day)));
}
