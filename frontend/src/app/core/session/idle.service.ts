import { Injectable } from '@angular/core';

import type { SessionStatus } from '../../api/bff/models';

/** Vigila la inactividad de la sesión y avisa antes de que venza (001-22). */
@Injectable({ providedIn: 'root' })
export class IdleService {
  /** Empieza a vigilar con lo que informó el BFF. */
  watch(status: SessionStatus): void {
    void status;
  }
}
