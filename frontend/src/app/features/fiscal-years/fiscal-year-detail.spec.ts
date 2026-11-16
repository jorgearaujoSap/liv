import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';

import type { FiscalYear, FiscalYearDetail, PreparationItem } from '../../api/tax/models';
import { TimeoutError } from '../../core/http/with-timeout';
import { FiscalYearDetailPage } from './fiscal-year-detail';
import { FiscalYearsService } from './fiscal-years.service';

const text = (element: Element | null | undefined) =>
  element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

/** Los nueve puntos del Doc. 01 §6.1, con su código, su número y si son obligatorios. */
const POINTS: readonly [number, string, boolean, string][] = [
  [1, 'FISCAL_YEAR_IN_PREPARATION', true, '1.4'],
  [2, 'VALUATION_RESOLUTION', true, '1.5.1'],
  [3, 'VALUATION_CATALOG_PUBLISHED', true, '1.5.2'],
  [4, 'TAX_RATES', true, '1.5.3'],
  [5, 'TAX_CALENDAR', true, '1.5.4'],
  [6, 'TAX_BENEFITS', false, '1.5.5'],
  [7, 'PENALTY_RULES', false, '1.5.6'],
  [8, 'SAMPLE_ASSESSMENT', true, '1.5.7'],
  [9, 'JURISDICTION_CONFIG_PUBLISHED', true, '1.5.8'],
];

const TITLES = [
  'Vigencia en preparación',
  'Resolución de avalúos registrada con su PDF',
  'Catálogo de avalúos publicado para la vigencia',
  'Tarifas por rango registradas y sin solapamientos',
  'Calendario tributario registrado',
  'Beneficios configurados',
  'Reglas de sanción registradas',
  'Prueba de liquidación de muestra sin errores',
  'Configuración de jurisdicción publicada',
];

const items = (planned: boolean): PreparationItem[] =>
  POINTS.map(([number, code, required, option]) => {
    const first = number === 1;
    return {
      number,
      code,
      required,
      option,
      status: first && !planned ? 'MET' : 'PENDING',
      reason: first ? (planned ? 'FISCAL_YEAR_PLANNED' : 'MET') : 'OPTION_NOT_AVAILABLE',
    };
  });

/** Una vigencia En preparación (con el punto 1 cumplido) o Planeada (con «Abrir preparación»). */
const detail = (year: number, planned: boolean): FiscalYearDetail => ({
  year,
  startsOn: `${year}-01-01`,
  endsOn: `${year}-12-31`,
  status: planned ? 'PLANEADA' : 'EN_PREPARACION',
  assessmentsEnabled: false,
  availableActions: planned ? ['OPEN_PREPARATION'] : [],
  preparation: {
    items: items(planned),
    missing: planned ? [1, 2, 3, 4, 5, 8, 9] : [2, 3, 4, 5, 8, 9],
    canEnable: false,
  },
});

class FakeFiscalYears {
  readonly get = vi.fn<(year: number) => Promise<FiscalYearDetail>>();
  readonly openPreparation = vi.fn<(year: number) => Promise<FiscalYear>>();
}

const problem = (status: number, body: Record<string, unknown>) =>
  new HttpErrorResponse({ status, error: { status, ...body } });

// 007-10, 007-11, 007-12 (V-03, CA-14), 007-02 y 007-21: el detalle de la vigencia y su lista de preparación.
// Diseño: Figma LIV-Diseño, sección 1.4 (66:1966): En preparación 68:3460, Planeada 68:3846, no encontrada
// 68:4235 y cargando 68:4474.
describe('Detalle de la vigencia (007-10, 007-11, 007-12)', () => {
  let service: FakeFiscalYears;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const setYear = (year: string | null) =>
    params.next(convertToParamMap(year === null ? {} : { year }));

  const render = async (year: string | null = '2026') => {
    setYear(year);
    const fixture = TestBed.createComponent(FiscalYearDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  };
  /** Deja correr las promesas de la pantalla (la API simulada) y vuelve a dibujar. */
  const settle = async (fixture: { detectChanges(): void; whenStable(): Promise<unknown> }) => {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  };
  const buttons = (host: HTMLElement) => Array.from(host.querySelectorAll('button'));
  const button = (host: HTMLElement, label: string) =>
    buttons(host).find((candidate) => text(candidate) === label);
  const checklist = (host: HTMLElement) => Array.from(host.querySelectorAll('[livChecklistItem]'));
  /** Los datos de la vigencia como «etiqueta valor» (el `dt` y el `dd` de cada uno). */
  const facts = (host: HTMLElement) =>
    Array.from(host.querySelectorAll('[livDescriptionItem]')).map(
      (item) => `${text(item.querySelector('dt'))} ${text(item.querySelector('dd'))}`,
    );
  const alertOf = (host: HTMLElement) => host.querySelector('liv-alert');

  beforeEach(() => {
    service = new FakeFiscalYears();
    params = new BehaviorSubject(convertToParamMap({ year: '2026' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: FiscalYearsService, useValue: service },
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
  });

  describe('los datos de la vigencia', () => {
    it('lleva el título con el año, la ruta de navegación y la descripción', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();

      expect(service.get).toHaveBeenCalledWith(2026);
      expect(text(host.querySelector('h1'))).toBe('Vigencia 2026');
      expect(text(host)).toContain('Administración 1.4 Vigencias fiscales 2026');
      expect(text(host)).toContain(
        'Estado de la vigencia y lista de preparación para habilitar la liquidación.',
      );
    });

    it('muestra año, inicio, fin, estado y liquidación, con las fechas en fecha larga (H2-13)', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();
      const panel = host.querySelector('liv-panel') as HTMLElement;

      expect(text(panel.querySelector('h2'))).toBe('Datos de la vigencia');
      expect(facts(panel)).toEqual([
        'Año 2026',
        'Inicio 1 de enero de 2026',
        'Fin 31 de diciembre de 2026',
        'Estado En preparación',
        'Liquidación No habilitada',
      ]);
    });

    it('«Planeada» sale con su texto, y la liquidación habilitada con el valor del backend', async () => {
      service.get.mockResolvedValue({
        ...detail(2027, true),
        status: 'HABILITADA',
        assessmentsEnabled: true,
      });
      const { host } = await render('2027');

      expect(facts(host).slice(3)).toEqual(['Estado Habilitada', 'Liquidación Habilitada']);
    });

    it('un estado que no conoce se muestra tal cual', async () => {
      service.get.mockResolvedValue({
        ...detail(2026, false),
        status: 'ARCHIVADA',
      } as unknown as FiscalYearDetail);
      const { host } = await render();

      expect(facts(host)).toContain('Estado ARCHIVADA');
    });
  });

  describe('la lista de preparación (007-11)', () => {
    it('muestra los nueve puntos, con el título que sale de su código y su número', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();
      const points = checklist(host);

      expect(points).toHaveLength(9);
      points.forEach((point, index) => {
        expect(text(point)).toMatch(new RegExp(`^${index + 1}[.] ?${TITLES[index]}`));
      });
      expect(host.querySelector('ol:has([livChecklistItem])')?.getAttribute('role')).toBe('list');
    });

    it('arma la frase de obligatorios y opcionales con `required`', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();

      expect(text(host)).toContain(
        'Los puntos 1 a 5, 8 y 9 son obligatorios; el 6 y el 7 son opcionales. Cada punto lo verifica el sistema.',
      );
    });

    it('la frase sigue lo que manda el backend, no los números del diseño', async () => {
      const everyOneRequired = detail(2026, false);
      everyOneRequired.preparation.items.forEach((item) => (item.required = item.number !== 3));
      service.get.mockResolvedValue(everyOneRequired);
      const { host } = await render();

      expect(text(host)).toContain('Los puntos 1, 2 y 4 a 9 son obligatorios; el 3 es opcional.');
    });

    it('marca como «Opcional» solo los puntos opcionales', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();

      const optional = checklist(host)
        .filter((point) => text(point).includes('Opcional'))
        .map((point) => text(point).slice(0, 2));
      expect(optional).toEqual(['6.', '7.']);
    });

    it('el punto cumplido dice «Cumplido» con su motivo, y los demás «Pendiente» con «Opción aún no disponible»', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();
      const points = checklist(host);

      expect(text(points[0])).toContain(
        'La vigencia ya está en preparación: sus tablas se pueden cargar.',
      );
      expect(text(points[0])).toContain('Cumplido');
      for (const point of points.slice(1)) {
        expect(text(point)).toContain('Pendiente');
        expect(text(point)).toContain('Opción aún no disponible');
        expect(text(point)).not.toContain('Cumplido');
      }
    });

    it('en una vigencia Planeada, el punto 1 está pendiente y dice por qué', async () => {
      service.get.mockResolvedValue(detail(2027, true));
      const { host } = await render('2027');

      expect(text(checklist(host)[0])).toContain('Pendiente');
      expect(text(checklist(host)[0])).toContain(
        'La vigencia está Planeada: abre su preparación para empezar a cargar sus tablas.',
      );
    });

    it('un punto o un motivo que no conoce no rompe la lista', async () => {
      const unknown = detail(2026, false);
      unknown.preparation.items.push({
        number: 10,
        code: 'PUNTO_NUEVO',
        required: false,
        option: '1.5.9',
        status: 'OBSERVED',
        reason: 'MOTIVO_NUEVO',
      });
      service.get.mockResolvedValue(unknown);
      const { host } = await render();

      expect(checklist(host)).toHaveLength(10);
      expect(text(checklist(host)[9])).toMatch(/^10\. ?Punto 10/);
      expect(text(checklist(host)[9])).not.toContain('MOTIVO_NUEVO');
    });
  });

  describe('«Habilitar liquidación» (V-03 · CA-14 · 007-12)', () => {
    const enableButton = (host: HTMLElement) => button(host, 'Habilitar liquidación');

    it('está inhabilitado y dice cuáles son los puntos que faltan, tomados de `missing`', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();
      const enable = enableButton(host) as HTMLButtonElement;
      const reason = host.querySelector(`#${enable.getAttribute('aria-describedby')}`);

      expect(enable.getAttribute('aria-disabled')).toBe('true');
      expect(text(reason)).toBe('Faltan 6 puntos obligatorios: 2, 3, 4, 5, 8 y 9.');
    });

    it('en una vigencia Planeada faltan siete, entre ellos el 1', async () => {
      service.get.mockResolvedValue(detail(2027, true));
      const { host } = await render('2027');

      expect(text(host)).toContain('Faltan 7 puntos obligatorios: 1, 2, 3, 4, 5, 8 y 9.');
    });

    it.each([
      [[5], 'Falta 1 punto obligatorio: 5.'],
      [[8, 9], 'Faltan 2 puntos obligatorios: 8 y 9.'],
      [[9, 2, 5], 'Faltan 3 puntos obligatorios: 2, 5 y 9.'],
    ])('con missing %j dice «%s»', async (missing, sentence) => {
      const base = detail(2026, false);
      service.get.mockResolvedValue({ ...base, preparation: { ...base.preparation, missing } });
      const { host } = await render();

      expect(text(host)).toContain(sentence);
    });

    it('sigue inhabilitado aunque el backend diga `canEnable`: en la 007 no hay habilitación', async () => {
      const base = detail(2026, false);
      service.get.mockResolvedValue({
        ...base,
        preparation: { ...base.preparation, canEnable: true, missing: [] },
      });
      const { host } = await render();
      const enable = enableButton(host) as HTMLButtonElement;

      expect(enable.getAttribute('aria-disabled')).toBe('true');
      expect(enable.getAttribute('aria-describedby')).not.toBeNull();
    });

    it('un clic no hace nada: no llama a la API', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { host } = await render();

      (enableButton(host) as HTMLButtonElement).click();

      expect(service.openPreparation).not.toHaveBeenCalled();
      expect(service.get).toHaveBeenCalledTimes(1);
    });
  });

  describe('«Abrir preparación» (007-10)', () => {
    it('solo se ofrece con la acción OPEN_PREPARATION', async () => {
      service.get.mockResolvedValue(detail(2027, true));
      const { host } = await render('2027');

      expect(button(host, 'Abrir preparación')).toBeDefined();
    });

    it('sin la acción no se ofrece, ni en una vigencia Planeada ni en las demás', async () => {
      for (const planned of [true, false]) {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: FiscalYearsService, useValue: service },
            { provide: ActivatedRoute, useValue: { paramMap: params } },
          ],
        });
        service.get.mockResolvedValue({ ...detail(2027, planned), availableActions: [] });
        const { host } = await render('2027');

        expect(button(host, 'Abrir preparación')).toBeUndefined();
      }
    });

    it('un valor desconocido de `availableActions` se ignora', async () => {
      service.get.mockResolvedValue({
        ...detail(2026, false),
        availableActions: ['ACCION_DEL_FUTURO'],
      });
      const { host } = await render();

      expect(button(host, 'Abrir preparación')).toBeUndefined();
      expect(text(host.querySelector('h1'))).toBe('Vigencia 2026');
    });

    it('abre la preparación del año de la ruta, vuelve a leer la vigencia y anuncia el resultado', async () => {
      service.get.mockResolvedValueOnce(detail(2027, true));
      service.get.mockResolvedValueOnce(detail(2027, false));
      service.openPreparation.mockResolvedValue({
        year: 2027,
        startsOn: '2027-01-01',
        endsOn: '2027-12-31',
        status: 'EN_PREPARACION',
        assessmentsEnabled: false,
      });
      const { fixture, host } = await render('2027');

      (button(host, 'Abrir preparación') as HTMLButtonElement).click();
      await settle(fixture);

      expect(service.openPreparation).toHaveBeenCalledExactlyOnceWith(2027);
      expect(service.get).toHaveBeenCalledTimes(2);
      expect(button(host, 'Abrir preparación')).toBeUndefined();
      expect(facts(host)).toContain('Estado En preparación');
      expect(text(checklist(host)[0])).toContain('Cumplido');
      expect(text(host.querySelector('[role="status"]'))).toBe(
        'Se abrió la preparación de la vigencia 2027.',
      );
    });

    it('mientras se envía, el botón está ocupado y un segundo clic no repite la llamada', async () => {
      service.get.mockResolvedValue(detail(2027, true));
      service.openPreparation.mockReturnValue(new Promise(() => undefined));
      const { fixture, host } = await render('2027');
      const open = button(host, 'Abrir preparación') as HTMLButtonElement;

      open.click();
      fixture.detectChanges();
      open.click();

      expect(service.openPreparation).toHaveBeenCalledTimes(1);
      expect(open.getAttribute('aria-disabled')).toBe('true');
      expect(Array.from(host.querySelectorAll('liv-button [role="status"]')).map(text)).toContain(
        'Abriendo preparación…',
      );
    });

    it('al abrirse, el foco no se pierde: pasa al título', async () => {
      service.get.mockResolvedValueOnce(detail(2027, true));
      service.get.mockResolvedValueOnce(detail(2027, false));
      service.openPreparation.mockResolvedValue({} as FiscalYear);
      const { fixture, host } = await render('2027');
      document.body.append(host);
      const open = button(host, 'Abrir preparación') as HTMLButtonElement;
      open.focus();

      open.click();
      await settle(fixture);

      expect(document.activeElement).toBe(host.querySelector('h1'));
      host.remove();
    });

    describe('rechazos', () => {
      const openWith = async (error: unknown, afterRead = detail(2027, false)) => {
        service.get.mockResolvedValueOnce(detail(2027, true));
        service.get.mockResolvedValue(afterRead);
        service.openPreparation.mockRejectedValue(error);
        const view = await render('2027');
        (button(view.host, 'Abrir preparación') as HTMLButtonElement).click();
        await settle(view.fixture);
        return view;
      };

      it('V-06: la alerta cita el código, va encima de los datos y la vigencia se vuelve a leer', async () => {
        const { host } = await openWith(problem(409, { code: 'V-06', year: 2027 }));
        const alert = alertOf(host) as HTMLElement;

        expect(alert.getAttribute('role')).toBe('alert');
        expect(text(alert)).toContain('No se abrió la preparación');
        expect(text(alert)).toContain(
          'V-06 · La vigencia 2027 ya no está Planeada: otra persona acaba de cambiarla. Actualizamos la pantalla.',
        );
        const firstPanel = host.querySelector('liv-panel') as HTMLElement;
        expect(
          alert.compareDocumentPosition(firstPanel) & Node.DOCUMENT_POSITION_FOLLOWING,
        ).toBeTruthy();
        expect(service.get).toHaveBeenCalledTimes(2);
        expect(button(host, 'Abrir preparación')).toBeUndefined();
        expect(facts(host)).toContain('Estado En preparación');
      });

      it('V-06 no escribe un año inventado por el servidor', async () => {
        const { host } = await openWith(problem(409, { code: 'V-06', year: '<b>x</b>' }));

        expect(text(alertOf(host))).toContain('La vigencia 2027 ya no está Planeada');
      });

      it('un error del servidor o el tiempo agotado: no se sabe si se abrió, y se vuelve a leer', async () => {
        for (const error of [problem(500, {}), new TimeoutError(10_000)]) {
          TestBed.resetTestingModule();
          TestBed.configureTestingModule({
            providers: [
              provideRouter([]),
              { provide: FiscalYearsService, useValue: (service = new FakeFiscalYears()) },
              { provide: ActivatedRoute, useValue: { paramMap: params } },
            ],
          });
          const { host } = await openWith(error);

          expect(text(alertOf(host))).toContain('No sabemos si se abrió la preparación');
          expect(service.get).toHaveBeenCalledTimes(2);
        }
      });

      it('otro rechazo da el mensaje general y no relee', async () => {
        const { host } = await openWith(problem(400, { code: 'OTRO' }));

        expect(text(alertOf(host))).toContain('No pudimos abrir la preparación.');
        expect(service.get).toHaveBeenCalledTimes(1);
        expect(button(host, 'Abrir preparación')).toBeDefined();
      });

      it('un 403 lo atiende el interceptor: la pantalla no agrega alerta', async () => {
        const { host } = await openWith(problem(403, { code: 'OTRO' }));

        expect(alertOf(host)).toBeNull();
      });

      it('la alerta se crea de nuevo en cada intento, para que se anuncie aunque el texto se repita', async () => {
        const first = await openWith(problem(400, { code: 'OTRO' }));
        const before = alertOf(first.host);

        (button(first.host, 'Abrir preparación') as HTMLButtonElement).click();
        await settle(first.fixture);

        expect(alertOf(first.host)).not.toBeNull();
        expect(alertOf(first.host)).not.toBe(before);
      });

      it('si la vigencia no se puede releer, se conserva lo que hay y se avisa', async () => {
        service.get.mockResolvedValueOnce(detail(2027, true));
        service.get.mockRejectedValue(new Error('500'));
        service.openPreparation.mockRejectedValue(problem(409, { code: 'V-06', year: 2027 }));
        const { fixture, host } = await render('2027');

        (button(host, 'Abrir preparación') as HTMLButtonElement).click();
        await settle(fixture);

        expect(text(host)).toContain('No pudimos actualizar la vigencia');
        expect(text(host.querySelector('h1'))).toBe('Vigencia 2027');
      });
    });
  });

  describe('el año de la ruta (007-02)', () => {
    it.each(['abc', '1998', '2101', '2026abc', '02026', '20.26', '-2026', '', ' 2026'])(
      'el parámetro «%s» muestra «no encontrada» sin llamar a la API',
      async (year) => {
        const { host } = await render(year);

        expect(service.get).not.toHaveBeenCalled();
        expect(text(host.querySelector('h1'))).toBe('Vigencia no encontrada');
        expect(text(host)).toContain('No encontramos esa vigencia');
      },
    );

    it('sin parámetro también es «no encontrada»', async () => {
      const { host } = await render(null);

      expect(service.get).not.toHaveBeenCalled();
      expect(text(host)).toContain('No encontramos esa vigencia');
    });

    it.each(['1999', '2100'])('los extremos del rango (%s) sí se consultan', async (year) => {
      service.get.mockResolvedValue(detail(Number(year), false));
      await render(year);

      expect(service.get).toHaveBeenCalledWith(Number(year));
    });
  });

  describe('vigencia no encontrada (404)', () => {
    const notFound = () => service.get.mockRejectedValue(problem(404, {}));

    it('lo dice como el diseño: título, error 404, descripción y la acción de volver', async () => {
      notFound();
      const { host } = await render('2031');

      expect(text(host.querySelector('h1'))).toBe('Vigencia no encontrada');
      expect(text(host)).toContain('Administración 1.4 Vigencias fiscales');
      expect(text(host)).toContain('Error 404');
      expect(text(host)).toContain('No encontramos esa vigencia');
      expect(text(host)).toContain(
        'La vigencia no existe en tu administración. Vuelve a la lista y elige otra.',
      );
      expect(host.querySelector('liv-panel')).toBeNull();
    });

    it('«Volver a las vigencias» lleva a la lista', async () => {
      notFound();
      const { host } = await render('2031');
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

      (button(host, 'Volver a las vigencias') as HTMLButtonElement).click();

      expect(navigate).toHaveBeenCalledWith('/opcion/1.4');
    });
  });

  describe('error al cargar', () => {
    it.each([problem(500, {}), new TimeoutError(10_000), new Error('sin red')])(
      'un fallo que no es 404 muestra el error, con «Reintentar»',
      async (error) => {
        service.get.mockRejectedValue(error);
        const { fixture, host } = await render();

        expect(text(host)).toContain('No pudimos cargar la vigencia');
        expect(text(host)).not.toContain('No encontramos esa vigencia');

        service.get.mockResolvedValue(detail(2026, false));
        (button(host, 'Reintentar') as HTMLButtonElement).click();
        await settle(fixture);

        expect(service.get).toHaveBeenCalledTimes(2);
        expect(text(host.querySelector('h1'))).toBe('Vigencia 2026');
        expect(text(host)).toContain('Datos de la vigencia');
      },
    );
  });

  describe('cargando', () => {
    it('muestra el marco con marcadores, ocupado, sin datos ni acciones', async () => {
      service.get.mockReturnValue(new Promise(() => undefined));
      const { host } = await render();

      expect(text(host.querySelector('h1'))).toBe('Vigencia');
      expect(host.querySelector('[aria-busy="true"]')).not.toBeNull();
      expect(host.querySelectorAll('liv-skeleton')).toHaveLength(7);
      expect(text(host)).toContain('Cargando los datos…');
      expect(text(host)).toContain('Cargando la lista de preparación…');
      expect(host.querySelector('[livChecklistItem]')).toBeNull();
      expect(buttons(host)).toHaveLength(0);
    });
  });

  describe('cambio de año en la misma pantalla', () => {
    it('al cambiar el año de la ruta se lee la vigencia nueva', async () => {
      service.get.mockImplementation((year) => Promise.resolve(detail(year, false)));
      const { fixture, host } = await render('2026');

      setYear('2027');
      await settle(fixture);

      expect(service.get).toHaveBeenLastCalledWith(2027);
      expect(text(host.querySelector('h1'))).toBe('Vigencia 2027');
    });

    it('la respuesta de una lectura anterior no pisa la de la vigencia nueva', async () => {
      let resolveOld: (value: FiscalYearDetail) => void = () => undefined;
      service.get.mockReturnValueOnce(new Promise((resolve) => (resolveOld = resolve)));
      service.get.mockResolvedValueOnce(detail(2027, false));
      const { fixture, host } = await render('2026');

      setYear('2027');
      await settle(fixture);
      resolveOld(detail(2026, false));
      await settle(fixture);

      expect(text(host.querySelector('h1'))).toBe('Vigencia 2027');
    });

    it('de un año válido a uno que no lo es muestra «no encontrada» sin llamar a la API', async () => {
      service.get.mockResolvedValue(detail(2026, false));
      const { fixture, host } = await render('2026');

      setYear('abc');
      await settle(fixture);

      expect(service.get).toHaveBeenCalledTimes(1);
      expect(text(host)).toContain('No encontramos esa vigencia');
    });
  });
});
