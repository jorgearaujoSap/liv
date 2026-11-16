import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import type { FiscalYear, FiscalYearDetail, FiscalYearList } from '../../api/tax/models';
import { TimeoutError } from '../../core/http/with-timeout';
import { FiscalYearsService } from './fiscal-years.service';

const FISCAL_YEAR_2026: FiscalYear = {
  year: 2026,
  startsOn: '2026-01-01',
  endsOn: '2026-12-31',
  status: 'EN_PREPARACION',
  assessmentsEnabled: false,
};

// 007-01, 007-03, 007-04, 007-09: el servicio solo llama al cliente generado de `tax` y entrega lo que
// el backend responde, sin decidir nada con el reloj ni con el rol.
describe('FiscalYearsService (007-01, 007-03, 007-04, 007-09)', () => {
  let service: FiscalYearsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FiscalYearsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('list pide GET /api/v1/fiscal-years y expone canCreate, creatableYears y createBlocked tal como vienen', async () => {
    const body: FiscalYearList = {
      items: [FISCAL_YEAR_2026],
      canCreate: true,
      creatableYears: [
        { year: 2027, startsOn: '2027-01-01', endsOn: '2027-12-31', relation: 'NEXT' },
        { year: 2025, startsOn: '2025-01-01', endsOn: '2025-12-31', relation: 'PAST' },
      ],
    };

    const result = service.list();
    const request = http.expectOne('/api/v1/fiscal-years');
    expect(request.request.method).toBe('GET');
    request.flush(body);

    const list = await result;
    expect(list).toEqual(body);
    expect(list.canCreate).toBe(true);
    expect(list.creatableYears.map((candidate) => candidate.relation)).toEqual(['NEXT', 'PAST']);
    // La API omite los campos nulos: sin permiso o sin bloqueo, `createBlocked` no viene.
    expect(list.createBlocked).toBeUndefined();
  });

  it('list entrega createBlocked con su motivo cuando el backend lo manda', async () => {
    const body: FiscalYearList = {
      items: [FISCAL_YEAR_2026],
      canCreate: true,
      creatableYears: [],
      createBlocked: { reason: 'ALL_CREATED', minYear: 2002, maxYear: 2027 },
    };

    const result = service.list();
    http.expectOne('/api/v1/fiscal-years').flush(body);

    expect((await result).createBlocked).toEqual({
      reason: 'ALL_CREATED',
      minYear: 2002,
      maxYear: 2027,
    });
  });

  it('create pide POST /api/v1/fiscal-years con el año como número', async () => {
    const result = service.create(2027);
    const request = http.expectOne('/api/v1/fiscal-years');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ year: 2027 });
    expect(typeof request.request.body.year).toBe('number');
    request.flush(
      { ...FISCAL_YEAR_2026, year: 2027, status: 'PLANEADA' },
      { status: 201, statusText: 'Created' },
    );

    expect((await result).status).toBe('PLANEADA');
  });

  it('create deja pasar el rechazo del servidor con su Problem Details', async () => {
    const result = service.create(2027);
    http.expectOne('/api/v1/fiscal-years').flush(
      { status: 409, code: 'V-01', year: 2027 },
      {
        status: 409,
        statusText: 'Conflict',
        headers: { 'Content-Type': 'application/problem+json' },
      },
    );

    await expect(result).rejects.toMatchObject({
      status: 409,
      error: { code: 'V-01', year: 2027 },
    });
  });

  it('get pide GET /api/v1/fiscal-years/{year}', async () => {
    const detail: FiscalYearDetail = {
      ...FISCAL_YEAR_2026,
      availableActions: [],
      preparation: { items: [], canEnable: false, missing: [] },
    };

    const result = service.get(2026);
    const request = http.expectOne('/api/v1/fiscal-years/2026');
    expect(request.request.method).toBe('GET');
    request.flush(detail);

    expect(await result).toEqual(detail);
  });

  it('openPreparation pide POST /api/v1/fiscal-years/{year}/preparation', async () => {
    const result = service.openPreparation(2026);
    const request = http.expectOne('/api/v1/fiscal-years/2026/preparation');
    expect(request.request.method).toBe('POST');
    request.flush(FISCAL_YEAR_2026);

    expect(await result).toEqual(FISCAL_YEAR_2026);
  });

  it('una respuesta que no llega en el tiempo límite falla con TimeoutError', async () => {
    vi.useFakeTimers();

    const result = service.list();
    const failure = expect(result).rejects.toBeInstanceOf(TimeoutError);
    await vi.advanceTimersByTimeAsync(10_000);

    await failure;
    http.expectOne('/api/v1/fiscal-years');
  });
});
