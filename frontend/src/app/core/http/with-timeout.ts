/** El BFF no respondió a tiempo. Se trata como cualquier otro fallo de red: no se sabe nada. */
export class TimeoutError extends Error {
  constructor(milliseconds: number) {
    super(`Sin respuesta en ${milliseconds} ms`);
    this.name = 'TimeoutError';
  }
}

/**
 * Limita la espera de una llamada. `HttpClient` no tiene tiempo límite: sin esto, un BFF que acepta la
 * conexión y no responde dejaría colgada la guarda de rutas, el aviso de inactividad y el cierre de
 * sesión.
 */
export function withTimeout<T>(call: Promise<T>, milliseconds: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new TimeoutError(milliseconds)), milliseconds);
    call.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error: unknown) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}
