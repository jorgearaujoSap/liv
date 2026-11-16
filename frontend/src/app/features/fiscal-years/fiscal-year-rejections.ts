import { HttpErrorResponse } from '@angular/common/http';

import { TimeoutError } from '../../core/http/with-timeout';

/** Lo que la pantalla muestra en una alerta de error cuando la API rechaza una operación sobre una vigencia. */
export interface Rejection {
  readonly heading: string;
  readonly message: string;
}

/** El rechazo de una creación: dice además si la lista que se muestra pudo quedar vieja. */
export interface CreationRejection extends Rejection {
  /** V-01 (otra persona creó el año) o un resultado que no se conoce (tiempo agotado, error del servidor). */
  readonly reload: boolean;
}

/** El rechazo de abrir la preparación: dice además si la vigencia cambió de estado. */
export interface PreparationRejection extends Rejection {
  /** V-06 */
  readonly refresh: boolean;
}

/** El código del 403 anti-CSRF (001-12): no es falta de permiso, y el interceptor no lleva a «Sin permiso». */
const CSRF_RULE_CODE = '001-12';

const CREATION_HEADING = 'No se creó la vigencia';
const CREATION_FAILED =
  'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.';
const CREATION_UNKNOWN_HEADING = 'No sabemos si se creó la vigencia';
const CREATION_UNKNOWN =
  'No recibimos respuesta del servidor. Es posible que la vigencia sí se haya creado: revisa la lista antes de intentarlo de nuevo.';
const PREPARATION_HEADING = 'No se abrió la preparación';
const PREPARATION_FAILED =
  'No pudimos abrir la preparación. Intenta de nuevo; si continúa, avisa al administrador del sistema.';

/**
 * El mensaje de un rechazo al crear la vigencia de `requestedYear` (V-01, V-02 y V-05; 007-05 a 007-08). Se
 * arma con el código de la regla y las extensiones del Problem Details (contrato `tax`), como en Figma
 * (67:3284), y nunca con el `detail` ni el `title` que mande el servidor. Un error que no es de una regla
 * conocida (o al que le falta el año que necesita su mensaje) cae en el mensaje general.
 *
 * Sin mensaje (`undefined`) queda el 403 de falta de permiso: lo atiende el interceptor, que lleva a «Sin
 * permiso» (001-16, H2-14). El 403 anti-CSRF y el 401 no siempre llevan a otra pantalla, así que sí tienen mensaje.
 */
export function creationRejection(
  error: unknown,
  requestedYear: number,
): CreationRejection | undefined {
  if (isHandledByInterceptor(error)) {
    return undefined;
  }
  const problem = problemOf(error);
  const code = problem?.['code'];
  if (problem && code === 'V-01') {
    const year = asYear(problem['year']) ?? requestedYear;
    return {
      heading: CREATION_HEADING,
      reload: true,
      message: `V-01 · La vigencia ${year} ya existe en tu administración: es posible que otra persona la haya creado hace un momento. Actualizamos la lista.`,
    };
  }
  const message = problem ? ruleMessage(code, problem) : undefined;
  if (message) {
    return { heading: CREATION_HEADING, reload: false, message };
  }
  // Sin respuesta (tiempo agotado, red) o con un error del servidor, la creación pudo haber ocurrido.
  if (isOutcomeUnknown(error)) {
    return { heading: CREATION_UNKNOWN_HEADING, reload: true, message: CREATION_UNKNOWN };
  }
  return { heading: CREATION_HEADING, reload: false, message: CREATION_FAILED };
}

/**
 * El mensaje de un rechazo al abrir la preparación de la vigencia de `year` (V-06; 007-10). Un V-06 dice que
 * otra persona ya cambió el estado: la pantalla vuelve a leer la vigencia.
 */
export function openPreparationRejection(
  error: unknown,
  year: number,
): PreparationRejection | undefined {
  if (isHandledByInterceptor(error)) {
    return undefined;
  }
  const problem = problemOf(error);
  if (problem?.['code'] === 'V-06') {
    const shown = asYear(problem['year']) ?? year;
    return {
      heading: PREPARATION_HEADING,
      refresh: true,
      message: `V-06 · La vigencia ${shown} ya no está Planeada: otra persona acaba de cambiarla. Actualizamos la pantalla.`,
    };
  }
  return { heading: PREPARATION_HEADING, refresh: false, message: PREPARATION_FAILED };
}

/** El mensaje de V-02 y V-05 según su motivo, o `undefined` si el código, el motivo o el año no son los esperados. */
function ruleMessage(code: unknown, problem: Record<string, unknown>): string | undefined {
  const reason = problem['reason'];
  const required = asYear(problem['requiredYear']);
  const oldest = asYear(problem['oldestYear']);
  const current = asYear(problem['currentYear']);
  const next = asYear(problem['nextYear']);
  const max = asYear(problem['maxYear']);

  if (code === 'V-02' && reason === 'GAP' && required !== undefined) {
    return `V-02 · No se puede saltar un año: primero debe existir la vigencia ${required}.`;
  }
  if (code === 'V-02' && reason === 'HISTORICAL' && oldest !== undefined) {
    return `V-02 · No se pueden crear vigencias anteriores a la más antigua (${oldest}): la carga de vigencias históricas todavía no está autorizada.`;
  }
  if (code === 'V-05' && reason === 'FIRST_YEAR' && current !== undefined && next !== undefined) {
    return `V-05 · La primera vigencia de la administración debe ser la del año en curso (${current}) o la del año siguiente (${next}).`;
  }
  if (code === 'V-05' && reason === 'FUTURE_LIMIT' && max !== undefined) {
    return `V-05 · No se puede crear una vigencia posterior al año siguiente al año en curso (${max}).`;
  }
  return undefined;
}

/**
 * El 403 de falta de permiso lo atiende el interceptor con una navegación a «Sin permiso» (001-16). No el 403
 * anti-CSRF (001-12), ni un 401 cuando no se pudo averiguar si hay sesión: en esos casos la pantalla debe decirlo.
 */
function isHandledByInterceptor(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    error.status === 403 &&
    problemOf(error)?.['code'] !== CSRF_RULE_CODE
  );
}

/** Si no se sabe si la operación llegó a hacerse: no hubo respuesta o el servidor falló. */
function isOutcomeUnknown(error: unknown): boolean {
  if (error instanceof TimeoutError) {
    return true;
  }
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}

/** El cuerpo del Problem Details de una respuesta de error, si es un objeto. */
function problemOf(error: unknown): Record<string, unknown> | undefined {
  if (!(error instanceof HttpErrorResponse)) {
    return undefined;
  }
  const body: unknown = error.error;
  return typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : undefined;
}

/** Los años de la tabla nacional de vigencias (el rango del contrato `tax`). */
const MIN_NATIONAL_YEAR = 1999;
const MAX_NATIONAL_YEAR = 2100;

/** Un año de una extensión: un entero de la tabla nacional, nunca un texto ni algo inventado. */
function asYear(value: unknown): number | undefined {
  const valid =
    typeof value === 'number' &&
    Number.isInteger(value) &&
    value >= MIN_NATIONAL_YEAR &&
    value <= MAX_NATIONAL_YEAR;
  return valid ? value : undefined;
}
