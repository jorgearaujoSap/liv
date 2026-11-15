import { HttpErrorResponse } from '@angular/common/http';

/** Lo que la pantalla muestra en una alerta de error cuando la API rechaza una operación sobre una vigencia. */
export interface Rejection {
  readonly heading: string;
  readonly message: string;
}

/** El rechazo de una creación: dice además si la lista cambió por debajo de la pantalla (V-01). */
export interface CreationRejection extends Rejection {
  /** V-01 */
  readonly conflict: boolean;
}

/** El rechazo de abrir la preparación: dice además si la vigencia cambió de estado (V-06). */
export interface PreparationRejection extends Rejection {
  /** V-06 */
  readonly refresh: boolean;
}

const CREATION_HEADING = 'No se creó la vigencia';
const CREATION_FAILED =
  'No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.';
const PREPARATION_HEADING = 'No se abrió la preparación';
const PREPARATION_FAILED =
  'No pudimos abrir la preparación. Intenta de nuevo; si continúa, avisa al administrador del sistema.';

/**
 * El mensaje de un rechazo al crear la vigencia de `requestedYear` (V-01, V-02 y V-05; 007-05 a 007-08). Se
 * arma con el código de la regla y las extensiones del Problem Details (contrato `tax`), como en Figma
 * (67:3284), y nunca con el `detail` ni el `title` que mande el servidor. Un error que no es de una regla
 * conocida (o al que le falta el año que necesita su mensaje) cae en el mensaje general.
 *
 * Sin mensaje (`undefined`) quedan el 401 y el 403: los atiende el interceptor, que lleva a iniciar sesión o
 * a «Sin permiso» (001-16, H2-14).
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
      conflict: true,
      message: `V-01 · La vigencia ${year} ya existe en tu administración: es posible que otra persona la haya creado hace un momento. Actualizamos la lista.`,
    };
  }
  const message = problem ? ruleMessage(code, problem) : undefined;
  return { heading: CREATION_HEADING, conflict: false, message: message ?? CREATION_FAILED };
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

/** El 401 y el 403 los atiende el interceptor de la API con una navegación: no llevan mensaje propio. */
function isHandledByInterceptor(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
}

/** El cuerpo del Problem Details de una respuesta de error, si es un objeto. */
function problemOf(error: unknown): Record<string, unknown> | undefined {
  if (!(error instanceof HttpErrorResponse)) {
    return undefined;
  }
  const body: unknown = error.error;
  return typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : undefined;
}

/** Un año de una extensión: solo un entero, nunca un texto, para no escribir en el mensaje algo inventado. */
function asYear(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) ? value : undefined;
}
