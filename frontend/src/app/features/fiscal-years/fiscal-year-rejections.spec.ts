import { HttpErrorResponse } from '@angular/common/http';

import { TimeoutError } from '../../core/http/with-timeout';
import { creationRejection, openPreparationRejection } from './fiscal-year-rejections';

const problem = (status: number, body: Record<string, unknown> | string | null) =>
  new HttpErrorResponse({ status, error: body });

// V-01, V-02, V-05 (007-05 a 007-08) y V-06 (007-10): el mensaje se arma con el código y las extensiones del
// Problem Details, como en Figma (67:3284); nada sale de un texto libre del servidor.
describe('mensajes de rechazo de las vigencias (007-05 a 007-08, 007-10)', () => {
  describe('creación', () => {
    it.each([
      [
        'V-01 · 007-05',
        problem(409, { status: 409, code: 'V-01', year: 2027 }),
        'V-01 · La vigencia 2027 ya existe en tu administración: es posible que otra persona la haya creado hace un momento. Actualizamos la lista.',
      ],
      [
        'V-05 PAST_LIMIT · 007-07',
        problem(422, { status: 422, code: 'V-05', reason: 'PAST_LIMIT', minYear: 2002 }),
        'V-05 · No se puede crear una vigencia anterior a 2002, el primer año que se puede crear.',
      ],
      [
        'V-05 FUTURE_LIMIT · 007-07',
        problem(422, { status: 422, code: 'V-05', reason: 'FUTURE_LIMIT', maxYear: 2027 }),
        'V-05 · No se puede crear una vigencia posterior al año siguiente al año en curso: el último año que se puede crear es 2027.',
      ],
    ])('%s', (_name, error, message) => {
      expect(creationRejection(error, 2028)).toEqual({
        heading: 'No se creó la vigencia',
        message,
        reload: message.startsWith('V-01'),
      });
    });

    // Desde el contrato 1.1.0 el servidor ya no emite estos motivos: si llegara uno, el mensaje es el general.
    it.each([
      ['V-02 GAP', { status: 422, code: 'V-02', reason: 'GAP', requiredYear: 2027 }],
      ['V-02 HISTORICAL', { status: 422, code: 'V-02', reason: 'HISTORICAL', oldestYear: 2026 }],
      [
        'V-05 FIRST_YEAR',
        { status: 422, code: 'V-05', reason: 'FIRST_YEAR', currentYear: 2026, nextYear: 2027 },
      ],
    ])('%s ya no se emite: cae en el mensaje general', (_name, body) => {
      expect(creationRejection(problem(422, body), 2028)).toEqual({
        heading: 'No se creó la vigencia',
        message:
          'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
        reload: false,
      });
    });

    it('V-01 sin el año en el cuerpo usa el que se intentó crear', () => {
      const rejection = creationRejection(problem(409, { status: 409, code: 'V-01' }), 2027);

      expect(rejection?.message).toContain('La vigencia 2027 ya existe');
      expect(rejection?.reload).toBe(true);
    });

    it('una regla conocida sin la extensión que necesita cae en el mensaje general', () => {
      const rejection = creationRejection(
        problem(422, { status: 422, code: 'V-05', reason: 'PAST_LIMIT' }),
        2029,
      );

      expect(rejection?.message).toBe(
        'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
      );
    });

    it('un año que no es un entero no se escribe en el mensaje', () => {
      const rejection = creationRejection(
        problem(422, { status: 422, code: 'V-05', reason: 'PAST_LIMIT', minYear: '2002' }),
        2029,
      );

      expect(rejection?.message).not.toContain('2002');
    });

    it('un motivo o un código que no conoce cae en el mensaje general', () => {
      for (const body of [
        { status: 422, code: 'V-05', reason: 'OTRO', minYear: 2002 },
        { status: 422, code: 'V-99' },
        { status: 400, code: 'SYS-400' },
      ]) {
        expect(creationRejection(problem(body.status, body), 2027)?.message).toBe(
          'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
        );
      }
    });

    it.each([
      ['un 500 sin cuerpo', problem(500, null)],
      ['un 502 con un cuerpo de texto', problem(502, '<html>Bad Gateway</html>')],
      ['un 503', problem(503, { status: 503 })],
      ['un sin respuesta (estado 0)', problem(0, null)],
      ['un tiempo agotado', new TimeoutError(10_000)],
    ])('%s: no se sabe si se creó, se dice y se pide releer la lista', (_name, error) => {
      expect(creationRejection(error, 2027)).toEqual({
        heading: 'No sabemos si se creó la vigencia',
        message:
          'No pudimos confirmar el resultado. Es posible que la vigencia sí se haya creado: revisa la lista antes de intentarlo de nuevo.',
        reload: true,
      });
    });

    it.each([
      ['un 400', problem(400, { status: 400, code: 'SYS-400' })],
      ['un error cualquiera', new Error('red')],
    ])('%s cae en el mensaje general, sin releer la lista', (_name, error) => {
      expect(creationRejection(error, 2027)).toEqual({
        heading: 'No se creó la vigencia',
        message:
          'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
        reload: false,
      });
    });

    it('un 403 que no es el anti-CSRF no tiene mensaje: el interceptor lleva a «Sin permiso» (H2-14)', () => {
      expect(
        creationRejection(problem(403, { status: 403, code: '001-16' }), 2027),
      ).toBeUndefined();
      expect(creationRejection(problem(403, null), 2027)).toBeUndefined();
    });

    it('un 403 anti-CSRF (001-12) no lo atiende el interceptor: se dice que no se creó', () => {
      expect(creationRejection(problem(403, { status: 403, code: '001-12' }), 2027)).toEqual({
        heading: 'No se creó la vigencia',
        message:
          'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
        reload: false,
      });
    });

    it('un 401 tampoco queda mudo: si no se pudo ir a iniciar sesión, se dice que no se creó', () => {
      expect(creationRejection(problem(401, { status: 401, code: '001-14' }), 2027)).toEqual({
        heading: 'No se creó la vigencia',
        message:
          'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
        reload: false,
      });
    });

    it('un 409 sin el código V-01 en el cuerpo se trata como V-01: el año ya existe', () => {
      const rejection = creationRejection(problem(409, null), 2027);

      expect(rejection?.message).toContain('V-01 · La vigencia 2027 ya existe');
      expect(rejection?.reload).toBe(true);
    });

    it('un año fuera de la tabla nacional (1999 a 2100) no se escribe en el mensaje', () => {
      for (const year of [-5, 0, 1998, 2101, 99999]) {
        const rejection = creationRejection(
          problem(422, { status: 422, code: 'V-05', reason: 'FUTURE_LIMIT', maxYear: year }),
          2029,
        );
        expect(rejection?.message).toBe(
          'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
        );
      }
    });

    it('no usa el texto libre del servidor', () => {
      const rejection = creationRejection(
        problem(409, { status: 409, code: 'V-01', year: 2027, detail: '<b>hola</b>', title: 'x' }),
        2027,
      );

      expect(rejection?.message).not.toContain('hola');
    });
  });

  describe('apertura de la preparación (007-10)', () => {
    it('V-06 dice que la vigencia ya no está Planeada y que se actualiza la pantalla', () => {
      const rejection = openPreparationRejection(
        problem(409, {
          status: 409,
          code: 'V-06',
          year: 2026,
          currentStatus: 'EN_PREPARACION',
        }),
        2026,
      );

      expect(rejection).toEqual({
        heading: 'No se abrió la preparación',
        message:
          'V-06 · La vigencia 2026 ya no está Planeada: otra persona acaba de cambiarla. Actualizamos la pantalla.',
        refresh: true,
      });
    });

    it('V-06 sin el año en el cuerpo usa el de la vigencia que se abrió', () => {
      const rejection = openPreparationRejection(problem(409, { status: 409, code: 'V-06' }), 2026);

      expect(rejection?.message).toContain('La vigencia 2026 ya no está Planeada');
    });

    it('un error de la petición cae en el mensaje general, sin volver a leer la vigencia', () => {
      expect(
        openPreparationRejection(problem(400, { status: 400, code: 'SYS-400' }), 2026),
      ).toEqual({
        heading: 'No se abrió la preparación',
        message:
          'No pudimos abrir la preparación. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
        refresh: false,
      });
    });

    it.each([
      ['un 500', problem(500, null)],
      ['un estado 0', problem(0, null)],
      ['un tiempo agotado', new TimeoutError(10_000)],
    ])('%s: no se sabe si se abrió, se dice y se pide releer la vigencia', (_name, error) => {
      expect(openPreparationRejection(error, 2026)).toEqual({
        heading: 'No sabemos si se abrió la preparación',
        message:
          'No pudimos confirmar el resultado. Es posible que la preparación sí se haya abierto: revisa la vigencia antes de intentarlo de nuevo.',
        refresh: true,
      });
    });

    it('un 403 que no es el anti-CSRF no tiene mensaje propio', () => {
      expect(
        openPreparationRejection(problem(403, { status: 403, code: '001-16' }), 2026),
      ).toBeUndefined();
    });

    it('un 403 anti-CSRF tiene el mensaje general', () => {
      expect(
        openPreparationRejection(problem(403, { status: 403, code: '001-12' }), 2026)?.message,
      ).toContain('No pudimos abrir la preparación');
    });
  });
});
