import { inject, Injectable } from '@angular/core';

import {
  createFiscalYear,
  getFiscalYear,
  listFiscalYears,
  openFiscalYearPreparation,
} from '../../api/tax/functions';
import type { FiscalYear, FiscalYearDetail, FiscalYearList } from '../../api/tax/models';
import { TaxApi } from '../../api/tax/tax-api';
import { withTimeout } from '../../core/http/with-timeout';

/** Lo que se espera a la API de vigencias antes de darla por caída. */
const REQUEST_TIMEOUT_MS = 10_000;

/**
 * Las vigencias fiscales de la administración (007-01 a 007-10). Solo llama al cliente generado de `tax` y
 * entrega lo que el backend responde: qué años se pueden crear, si hay permiso y por qué un botón queda
 * inhabilitado lo decide el servidor, nunca el reloj ni el rol del navegador (007-09).
 */
@Injectable({ providedIn: 'root' })
export class FiscalYearsService {
  private readonly tax = inject(TaxApi);

  /** Las vigencias en el orden que responde el backend, con `canCreate`, `creatableYears` y `createBlocked`. */
  list(): Promise<FiscalYearList> {
    return this.withRequestTimeout(this.tax.invoke(listFiscalYears));
  }

  /** Crea la vigencia de un año, en PLANEADA. Un rechazo llega como el error HTTP con su Problem Details. */
  create(year: number): Promise<FiscalYear> {
    return this.withRequestTimeout(this.tax.invoke(createFiscalYear, { body: { year } }));
  }

  /** Una vigencia con sus acciones y su lista de preparación. */
  get(year: number): Promise<FiscalYearDetail> {
    return this.withRequestTimeout(this.tax.invoke(getFiscalYear, { year }));
  }

  /** Abre la preparación de la vigencia, de PLANEADA a EN_PREPARACION. */
  openPreparation(year: number): Promise<FiscalYear> {
    return this.withRequestTimeout(this.tax.invoke(openFiscalYearPreparation, { year }));
  }

  private withRequestTimeout<T>(request: Promise<T>): Promise<T> {
    return withTimeout(request, REQUEST_TIMEOUT_MS);
  }
}
