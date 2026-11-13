import { formatBusinessDate } from './business-date';

/** Las pruebas corren en Node, pero `tsconfig.spec.json` no carga sus tipos (no hay `@types/node`). */
declare const process: { env: Record<string, string | undefined> };

/**
 * Colombia está en UTC−5 todo el año. En UTC el defecto que esta prueba vigila (`new Date('2026-01-01')`
 * muestra el 31 de diciembre de 2025) no se vería, así que la prueba fija la zona y comprueba que quedó
 * activa antes de formatear nada.
 */
const BOGOTA_OFFSET_MINUTES = 300;

const originalZone = process.env['TZ'];

function useZone(zone: string): void {
  process.env['TZ'] = zone;
}

afterAll(() => {
  if (originalZone === undefined) delete process.env['TZ'];
  else process.env['TZ'] = originalZone;
});

describe('formatBusinessDate', () => {
  beforeEach(() => {
    useZone('America/Bogota');
    // Cada prueba vigila el defecto solo si la zona quedó activa: si no, falla aquí, no pasa en silencio.
    expect(new Date(2026, 0, 1).getTimezoneOffset()).toBe(BOGOTA_OFFSET_MINUTES);
  });

  it('la prueba corre en la zona de Bogotá; si no, no vería el defecto que vigila', () => {
    expect(new Date(2026, 0, 1).getTimezoneOffset()).toBe(BOGOTA_OFFSET_MINUTES);
    // El defecto: interpretar la fecha como UTC la corre un día atrás en Bogotá.
    expect(new Date('2026-01-01').getDate()).toBe(31);
  });

  describe('fecha larga (H2-13)', () => {
    it.each([
      ['2026-01-01', '1 de enero de 2026'],
      ['2026-12-31', '31 de diciembre de 2026'],
      ['2027-02-28', '28 de febrero de 2027'],
      ['2028-02-29', '29 de febrero de 2028'],
      ['2026-03-09', '9 de marzo de 2026'],
    ])('%s se muestra «%s»', (value, expected) => {
      expect(formatBusinessDate(value, 'long')).toBe(expected);
    });

    it('da los doce meses en español, en minúscula, sin ceros a la izquierda en el día', () => {
      const months = [
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
      ];
      months.forEach((month, index) => {
        const mm = String(index + 1).padStart(2, '0');
        expect(formatBusinessDate(`2026-${mm}-05`, 'long')).toBe(`5 de ${month} de 2026`);
      });
    });

    it('es el formato por omisión', () => {
      expect(formatBusinessDate('2026-01-01')).toBe('1 de enero de 2026');
    });
  });

  describe('fecha corta', () => {
    it.each([
      ['2026-01-01', '01/01/2026'],
      ['2026-12-31', '31/12/2026'],
      ['2027-02-05', '05/02/2027'],
      ['2028-02-29', '29/02/2028'],
    ])('%s se muestra %s (dd/mm/aaaa)', (value, expected) => {
      expect(formatBusinessDate(value, 'short')).toBe(expected);
    });
  });

  describe('sin convertir de zona', () => {
    it.each([
      ['América/Bogotá (UTC−5)', 'America/Bogota', 300],
      ['UTC', 'UTC', 0],
      ['Pago Pago (UTC−11)', 'Pacific/Pago_Pago', 660],
      ['Kiritimati (UTC+14)', 'Pacific/Kiritimati', -840],
      ['Madrid (UTC+1 en enero, con horario de verano)', 'Europe/Madrid', -60],
      ['Santiago (UTC−3 en enero, con horario de verano)', 'America/Santiago', 180],
    ])('el día no se corre en %s', (_name, zone, offset) => {
      useZone(zone);
      expect(new Date(2026, 0, 1).getTimezoneOffset()).toBe(offset);
      expect(formatBusinessDate('2026-01-01', 'long')).toBe('1 de enero de 2026');
      expect(formatBusinessDate('2026-12-31', 'long')).toBe('31 de diciembre de 2026');
      expect(formatBusinessDate('2026-01-01', 'short')).toBe('01/01/2026');
      expect(formatBusinessDate('2026-12-31', 'short')).toBe('31/12/2026');
      // Los días del cambio de hora de Madrid (marzo y octubre) no se corren ni se repiten.
      expect(formatBusinessDate('2026-03-29', 'long')).toBe('29 de marzo de 2026');
      expect(formatBusinessDate('2026-10-25', 'short')).toBe('25/10/2026');
    });
  });

  describe('un valor que no es una fecha de negocio', () => {
    it.each([
      ['vacío', ''],
      ['con espacios', ' 2026-01-01 '],
      ['con un salto de línea al final', '2026-01-01\n'],
      ['sin ceros', '2026-1-1'],
      ['día y mes al revés', '01/01/2026'],
      ['con hora', '2026-01-01T00:00:00Z'],
      ['con hora local', '2026-01-01T00:00:00'],
      ['mes 13', '2026-13-01'],
      ['mes 00', '2026-00-10'],
      ['día 00', '2026-01-00'],
      ['día 32', '2026-01-32'],
      ['30 de febrero', '2026-02-30'],
      ['29 de febrero sin año bisiesto', '2027-02-29'],
      ['31 de abril', '2026-04-31'],
      ['año de dos cifras', '26-01-01'],
      ['año 0000', '0000-01-01'],
      ['texto', 'mañana'],
    ])('%s da null, para que la pantalla no muestre una fecha inventada', (_name, value) => {
      expect(formatBusinessDate(value, 'long')).toBeNull();
      expect(formatBusinessDate(value, 'short')).toBeNull();
    });

    it('un valor ausente (null o undefined) da null', () => {
      expect(formatBusinessDate(null)).toBeNull();
      expect(formatBusinessDate(undefined)).toBeNull();
    });

    it('un valor que no es un texto (un número o un objeto, por un cast mal hecho) da null', () => {
      expect(formatBusinessDate(20260101 as unknown as string)).toBeNull();
      expect(formatBusinessDate(new Date(2026, 0, 1) as unknown as string)).toBeNull();
    });
  });
});
