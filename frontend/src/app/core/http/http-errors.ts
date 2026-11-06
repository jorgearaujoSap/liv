import { HttpErrorResponse } from '@angular/common/http';

/** Dice si el error es una respuesta HTTP del servidor con ese estado. */
export function hasStatus(error: unknown, status: number): boolean {
  return error instanceof HttpErrorResponse && error.status === status;
}
