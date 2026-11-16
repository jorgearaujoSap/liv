import { HttpErrorResponse } from '@angular/common/http';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import type { FiscalYear, FiscalYearList } from '../../api/tax/models';
import { TimeoutError } from '../../core/http/with-timeout';
import { FiscalYears } from './fiscal-years';
import { FiscalYearsService } from './fiscal-years.service';

const text = (element: Element | null | undefined) =>
  element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

const fiscalYear = (year: number, status: FiscalYear['status'] = 'PLANEADA'): FiscalYear => ({
  year,
  startsOn: `${year}-01-01`,
  endsOn: `${year}-12-31`,
  status,
  assessmentsEnabled: false,
});

const NEXT_2027 = { year: 2027, startsOn: '2027-01-01', endsOn: '2027-12-31', relation: 'NEXT' };
const CURRENT_2026 = {
  year: 2026,
  startsOn: '2026-01-01',
  endsOn: '2026-12-31',
  relation: 'CURRENT',
};

/** Los años de 2002 a 2027 como los manda el contrato 1.1.0: del más reciente al más antiguo. */
const FULL_RANGE = [
  NEXT_2027,
  CURRENT_2026,
  ...Array.from({ length: 24 }, (_unused, index) => {
    const year = 2025 - index;
    return { year, startsOn: `${year}-01-01`, endsOn: `${year}-12-31`, relation: 'PAST' };
  }),
];

/** Todas las vigencias de 2002 a 2027 ya existen (007-23). */
const ALL_CREATED = { reason: 'ALL_CREATED', minYear: 2002, maxYear: 2027 };

const withRows: FiscalYearList = {
  items: [fiscalYear(2027), fiscalYear(2026, 'EN_PREPARACION')],
  canCreate: true,
  creatableYears: [],
};

class FakeFiscalYears {
  readonly list = vi.fn<() => Promise<FiscalYearList>>();
  readonly create = vi.fn();
}

// 007-01, 007-03, 007-09: la lista de la opción 1.4. Diseño: Figma LIV-Diseño, sección 1.4 (66:1966).
describe('Vigencias fiscales (007-01, 007-03, 007-09)', () => {
  let service: FakeFiscalYears;

  const render = async (list: FiscalYearList | Promise<FiscalYearList>) => {
    service.list.mockReturnValue(Promise.resolve(list));
    const fixture = TestBed.createComponent(FiscalYears);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };
  const buttons = (host: HTMLElement) => Array.from(host.querySelectorAll('button'));
  const button = (host: HTMLElement, label: string) =>
    buttons(host).find((candidate) => text(candidate) === label);
  const rows = (host: HTMLElement) =>
    Array.from(host.querySelectorAll('tbody tr')).map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => text(cell)),
    );

  beforeEach(() => {
    service = new FakeFiscalYears();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: FiscalYearsService, useValue: service }],
    });
  });

  describe('con vigencias (007-01)', () => {
    it('lleva el título, la ruta de navegación y la descripción', async () => {
      const host = await render(withRows);

      expect(text(host.querySelector('h1'))).toBe('Vigencias fiscales');
      expect(text(host)).toContain('Administración 1.4 Vigencias fiscales');
      expect(text(host)).toContain(
        'Cada vigencia es un año fiscal: en ella se cargan las tablas base y se habilita la liquidación.',
      );
    });

    it('lista las vigencias como vienen, de la más reciente a la más antigua, con fechas dd/mm/aaaa', async () => {
      const host = await render(withRows);

      expect(rows(host)).toEqual([
        ['2027', '01/01/2027', '31/12/2027', 'Planeada', 'No habilitada', ''],
        ['2026', '01/01/2026', '31/12/2026', 'En preparación', 'No habilitada', ''],
      ]);
    });

    it('la tabla tiene nombre accesible y encabezados de columna', async () => {
      const host = await render(withRows);

      expect(text(host.querySelector('table caption, table[aria-label]'))).not.toBe('');
      expect(Array.from(host.querySelectorAll('thead th')).map((cell) => text(cell))).toEqual([
        'Año',
        'Inicio',
        'Fin',
        'Estado',
        'Liquidación',
        'Ver detalle',
      ]);
    });

    it('dice si la liquidación está habilitada con el valor que manda el backend', async () => {
      const enabled: FiscalYear = { ...fiscalYear(2026, 'HABILITADA'), assessmentsEnabled: true };
      const host = await render({ ...withRows, items: [enabled] });

      expect(rows(host)[0].slice(3, 5)).toEqual(['Habilitada', 'Habilitada']);
    });

    it('muestra cada estado con su texto; uno que no conoce, tal cual lo recibe', async () => {
      const unknown = { ...fiscalYear(2022), status: 'ARCHIVADA' } as unknown as FiscalYear;
      const host = await render({
        ...withRows,
        items: [
          fiscalYear(2027),
          fiscalYear(2026, 'EN_PREPARACION'),
          fiscalYear(2025, 'LISTA'),
          fiscalYear(2024, 'HABILITADA'),
          fiscalYear(2023, 'CERRADA'),
          unknown,
        ],
      });

      expect(rows(host).map((row) => row[3])).toEqual([
        'Planeada',
        'En preparación',
        'Lista',
        'Habilitada',
        'Cerrada',
        'ARCHIVADA',
      ]);
    });

    it('un año enlaza al detalle de su vigencia, por una ruta propia', async () => {
      const host = await render(withRows);
      const router = TestBed.inject(Router);
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

      const link = host.querySelector('tbody a') as HTMLAnchorElement;
      expect(link.getAttribute('href')).toBe('/opcion/1.4/2027');
      link.click();

      expect(navigate).toHaveBeenCalledWith('/opcion/1.4/2027');
    });

    it('una fecha que no es válida no se inventa', async () => {
      const broken = { ...fiscalYear(2026), startsOn: '2026-02-30' };
      const host = await render({ ...withRows, items: [broken] });

      expect(rows(host)[0][1]).toBe('Sin fecha');
    });

    it('con permiso para crear muestra «Crear vigencia»', async () => {
      const host = await render({ ...withRows, creatableYears: [NEXT_2027] });

      const create = button(host, 'Crear vigencia');
      expect(create).toBeDefined();
      expect(create?.getAttribute('aria-disabled')).not.toBe('true');
      expect(create?.disabled).toBe(false);
    });
  });

  // 007-09: las acciones se muestran según lo que dice el backend, no según el rol.
  describe('sin permiso para crear (007-09)', () => {
    it('con vigencias, no muestra la acción', async () => {
      const host = await render({ ...withRows, canCreate: false });

      expect(button(host, 'Crear vigencia')).toBeUndefined();
      expect(rows(host)).toHaveLength(2);
    });

    it('sin vigencias, no muestra la acción y dice quién puede crearlas', async () => {
      const host = await render({ items: [], canCreate: false, creatableYears: [] });

      expect(button(host, 'Crear la primera vigencia')).toBeUndefined();
      expect(button(host, 'Crear vigencia')).toBeUndefined();
      expect(text(host.querySelector('h2'))).toBe('Aún no hay vigencias');
      expect(text(host)).toContain(
        'Tu administración todavía no tiene vigencias fiscales. Solo el administrador del sistema puede crearlas.',
      );
    });
  });

  describe('sin vigencias (007-03)', () => {
    it('explica con ResultState el rango de años que admite la primera, tomado de los extremos de lo que manda el backend (66:2793)', async () => {
      const host = await render({ items: [], canCreate: true, creatableYears: FULL_RANGE });

      expect(host.querySelector('table')).toBeNull();
      expect(text(host.querySelector('h2'))).toBe('Aún no hay vigencias');
      expect(text(host)).toContain(
        'Crea la primera vigencia de tu administración. Puedes crear cualquier año de 2002 a 2027.',
      );
    });

    it('el rango sale del último y del primer año de la lista, no de una cuenta propia ni de una constante', async () => {
      const host = await render({
        items: [],
        canCreate: true,
        creatableYears: [
          { ...NEXT_2027, year: 2031 },
          { ...CURRENT_2026, year: 2030 },
          { ...CURRENT_2026, year: 2010, relation: 'PAST' },
        ],
      });

      expect(text(host)).toContain('Puedes crear cualquier año de 2010 a 2031.');
    });

    it('el rango no depende del orden de la lista: en orden ascendente dice lo mismo', async () => {
      const host = await render({
        items: [],
        canCreate: true,
        creatableYears: [...FULL_RANGE].reverse(),
      });

      expect(text(host)).toContain('Puedes crear cualquier año de 2002 a 2027.');
    });

    it('con un solo año en la lista, dice ese año y no «de 2027 a 2027»', async () => {
      const host = await render({ items: [], canCreate: true, creatableYears: [NEXT_2027] });

      expect(text(host)).toContain(
        'Crea la primera vigencia de tu administración. Puedes crear el año 2027.',
      );
    });

    it('sin ningún año en la lista, no inventa un rango', async () => {
      const host = await render({ items: [], canCreate: true, creatableYears: [] });

      expect(text(host)).not.toContain('Puedes crear cualquier año');
    });

    it('un año que no es entero no se escribe en el rango', async () => {
      const host = await render({
        items: [],
        canCreate: true,
        creatableYears: [
          { ...NEXT_2027, year: '2027' as unknown as number },
          { ...CURRENT_2026, year: 2002, relation: 'PAST' },
        ],
      });

      expect(text(host)).not.toContain('Puedes crear cualquier año');
      expect(text(host)).toContain('Crea la primera vigencia de tu administración.');
    });

    it('con permiso ofrece «Crear la primera vigencia»', async () => {
      const host = await render({ items: [], canCreate: true, creatableYears: FULL_RANGE });

      expect(button(host, 'Crear la primera vigencia')).toBeDefined();
      expect(button(host, 'Crear vigencia')).toBeUndefined();
    });
  });

  // V-05 · 007-23: con todo creado, de 2002 al año siguiente al año en curso, no hay nada que ofrecer.
  describe('sin años disponibles: ya existen todas las vigencias (007-23, 66:2513)', () => {
    const blocked: FiscalYearList = {
      items: [fiscalYear(2027), fiscalYear(2026, 'EN_PREPARACION')],
      canCreate: true,
      creatableYears: [],
      createBlocked: ALL_CREATED,
    };
    const ALL_CREATED_REASON =
      'Ya existen todas las vigencias que se pueden crear: de 2002 a 2027.';

    it('deja «Crear vigencia» inhabilitado y dice el motivo debajo, con el rango que manda el servidor', async () => {
      const host = await render(blocked);

      const create = button(host, 'Crear vigencia');
      expect(create?.getAttribute('aria-disabled')).toBe('true');
      expect(text(host)).toContain(ALL_CREATED_REASON);
      const reasonId = create?.getAttribute('aria-describedby') ?? '';
      expect(text(host.querySelector(`[id="${reasonId}"]`))).toBe(ALL_CREATED_REASON);
    });

    it('el rango sale de minYear y maxYear, no de una constante', async () => {
      const host = await render({
        ...blocked,
        createBlocked: { reason: 'ALL_CREATED', minYear: 2003, maxYear: 2028 },
      });

      expect(text(host)).toContain(
        'Ya existen todas las vigencias que se pueden crear: de 2003 a 2028.',
      );
    });

    it('con FUTURE_LIMIT, que desde el contrato 1.1.0 ya no se emite, dice el texto general', async () => {
      const host = await render({
        ...blocked,
        createBlocked: { reason: 'FUTURE_LIMIT', maxYear: 2027 },
      });

      expect(button(host, 'Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');
      expect(text(host)).toContain('No hay años disponibles para crear una vigencia.');
      expect(text(host)).not.toContain('la última que se puede crear');
    });

    it('sin vigencias y con todo creado, el estado vacío tampoco pide crear la primera', async () => {
      const host = await render({ ...blocked, items: [] });

      expect(text(host)).not.toContain('Crea la primera vigencia de tu administración');
      expect(text(host)).toContain(ALL_CREATED_REASON);
      expect(button(host, 'Crear la primera vigencia')?.getAttribute('aria-disabled')).toBe('true');
    });

    it('no abre nada al hacer clic', async () => {
      const host = await render(blocked);

      button(host, 'Crear vigencia')?.click();
      TestBed.tick();

      expect(host.querySelector('[role="dialog"]')).toBeNull();
    });

    it('con un motivo que no conoce, dice que no hay años disponibles', async () => {
      const host = await render({
        ...blocked,
        createBlocked: { reason: 'OTRO_MOTIVO', minYear: 2002, maxYear: 2027 },
      });

      expect(button(host, 'Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');
      expect(text(host)).toContain('No hay años disponibles para crear una vigencia.');
    });
  });

  describe('cargando y error', () => {
    it('cargando muestra la tabla ocupada con marcadores y ningún dato', async () => {
      service.list.mockReturnValue(new Promise<FiscalYearList>(() => undefined));
      const fixture = TestBed.createComponent(FiscalYears);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('table[aria-busy="true"]')).not.toBeNull();
      expect(host.querySelectorAll('tbody liv-skeleton').length).toBeGreaterThan(0);
      expect(host.querySelector('tbody a')).toBeNull();
      expect(button(host, 'Crear vigencia')).toBeUndefined();
    });

    it('si la API falla, dice qué pasó y deja reintentar', async () => {
      service.list.mockRejectedValueOnce(new Error('500'));
      const fixture = TestBed.createComponent(FiscalYears);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      expect(text(host.querySelector('h2'))).toBe('No pudimos cargar las vigencias');
      expect(text(host)).toContain(
        'Ocurrió un problema al consultar las vigencias de tu administración. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
      );
      expect(host.querySelector('table')).toBeNull();

      service.list.mockResolvedValueOnce(withRows);
      button(host, 'Reintentar')?.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text(host.querySelector('h1'))).toBe('Vigencias fiscales');
      expect(rows(host)).toHaveLength(2);
      expect(host.querySelector('h2')).toBeNull();
    });

    it('una respuesta que no llega a tiempo también es un error', async () => {
      service.list.mockRejectedValueOnce(new TimeoutError(10_000));
      const fixture = TestBed.createComponent(FiscalYears);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text((fixture.nativeElement as HTMLElement).querySelector('h2'))).toBe(
        'No pudimos cargar las vigencias',
      );
    });
  });

  // Lo que el backend puede mandar fuera de lo documentado: la pantalla no inventa textos ni se rompe.
  describe('datos fuera de lo documentado', () => {
    it('un estado que coincide con una propiedad de Object se muestra tal cual', async () => {
      const odd = { ...fiscalYear(2026), status: 'constructor' } as unknown as FiscalYear;
      const host = await render({ ...withRows, items: [odd] });

      expect(rows(host)[0][3]).toBe('constructor');
    });

    it.each([
      ['maxYear que no es entero', { reason: 'ALL_CREATED', minYear: 2002, maxYear: '2027' }],
      ['minYear que no es entero', { reason: 'ALL_CREATED', minYear: '2002', maxYear: 2027 }],
      ['sin minYear', { reason: 'ALL_CREATED', maxYear: 2027 }],
      ['sin maxYear', { reason: 'ALL_CREATED', minYear: 2002 }],
      ['años fuera de la tabla nacional', { reason: 'ALL_CREATED', minYear: 99999, maxYear: 2027 }],
      ['minYear mayor que maxYear', { reason: 'ALL_CREATED', minYear: 2030, maxYear: 2002 }],
    ])('un bloqueo ALL_CREATED con %s cae en el texto general', async (_name, blocked) => {
      const host = await render({
        ...withRows,
        creatableYears: [],
        createBlocked: blocked as unknown as FiscalYearList['createBlocked'],
      });

      expect(button(host, 'Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');
      expect(text(host)).toContain('No hay años disponibles para crear una vigencia.');
      expect(text(host)).not.toContain('undefined');
    });

    it('con permiso pero sin ningún año que ofrecer, la acción queda inhabilitada con su motivo', async () => {
      const host = await render({ ...withRows, canCreate: true, creatableYears: [] });

      expect(button(host, 'Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');
      expect(text(host)).toContain('No hay años disponibles para crear una vigencia.');
    });

    it('un año que no es entero no se vuelve un enlace', async () => {
      const odd = { ...fiscalYear(2026), year: 2026.5 };
      const host = await render({ ...withRows, items: [odd] });

      expect(host.querySelector('tbody a[href]')).toBeNull();
    });
  });

  // Una respuesta que llega tarde no pisa a una más nueva (el orden de las lecturas).
  describe('lecturas desordenadas', () => {
    const deferred = () => {
      let resolve: (list: FiscalYearList) => void = () => undefined;
      let reject: (error: unknown) => void = () => undefined;
      const promise = new Promise<FiscalYearList>((res, rej) => {
        resolve = res;
        reject = rej;
      });
      return { promise, resolve, reject };
    };
    const start = () => {
      const first = deferred();
      const second = deferred();
      service.list.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
      const fixture = TestBed.createComponent(FiscalYears);
      fixture.detectChanges();
      const screen = fixture.componentInstance as unknown as { load(): Promise<unknown> };
      const retry = screen.load();
      return { fixture, first, second, retry, host: fixture.nativeElement as HTMLElement };
    };

    it('la respuesta de una lectura anterior no pisa la de la última', async () => {
      const { fixture, first, second, retry, host } = start();

      second.resolve({ ...withRows, items: [fiscalYear(2030)] });
      await retry;
      first.resolve({ ...withRows, items: [fiscalYear(2020)] });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(rows(host).map((row) => row[0])).toEqual(['2030']);
    });

    it('el fallo de una lectura anterior no tapa la lista de la última', async () => {
      const { fixture, first, second, retry, host } = start();

      second.resolve(withRows);
      await retry;
      first.reject(new Error('500'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(rows(host)).toHaveLength(2);
      expect(host.querySelector('h2')).toBeNull();
    });
  });

  // 007-04, 007-05 y 007-21: la lista abre el diálogo de creación y vuelve a leer lo que cambió.
  describe('crear desde la lista (007-04, 007-05)', () => {
    // En estas pruebas el año en curso es 2027 y el siguiente, 2028; el servidor los manda del más reciente al más antiguo.
    const current2027 = { ...NEXT_2027, relation: 'CURRENT' };
    const year2028 = { ...NEXT_2027, year: 2028, startsOn: '2028-01-01', endsOn: '2028-12-31' };
    const creatable: FiscalYearList = {
      items: [fiscalYear(2026, 'EN_PREPARACION')],
      canCreate: true,
      creatableYears: [year2028, current2027],
    };
    const conflict = () =>
      new HttpErrorResponse({ status: 409, error: { status: 409, code: 'V-01', year: 2027 } });
    const dialog = (host: HTMLElement) => host.querySelector('[role="dialog"]');
    const years = (host: HTMLElement) =>
      Array.from(host.querySelectorAll<HTMLOptionElement>('liv-select option'))
        .map((candidate) => candidate.value)
        .filter(Boolean);
    const chosen = (host: HTMLElement) =>
      host.querySelector<HTMLSelectElement>('liv-select select')?.value || undefined;
    const inDialog = (host: HTMLElement, label: string) =>
      Array.from(dialog(host)?.querySelectorAll('button') ?? []).find(
        (candidate) => text(candidate) === label,
      );
    const open = async (list: FiscalYearList = creatable) => {
      const host = await render(list);
      const label = list.items.length === 0 ? 'Crear la primera vigencia' : 'Crear vigencia';
      button(host, label)?.click();
      TestBed.tick();
      return host;
    };
    const create = async (host: HTMLElement) => {
      inDialog(host, 'Crear vigencia')?.click();
      await TestBed.inject(ApplicationRef).whenStable();
      TestBed.tick();
    };

    it('«Crear vigencia» abre el diálogo con los años que mandó el backend', async () => {
      const host = await open();

      expect(dialog(host)).not.toBeNull();
      expect(years(host)).toEqual(['2028', '2027']);
      expect(chosen(host)).toBe('2027');
    });

    it('«Crear la primera vigencia» también lo abre', async () => {
      const host = await open({ ...creatable, items: [] });

      expect(dialog(host)).not.toBeNull();
    });

    it('«Cancelar» lo cierra sin pedir nada', async () => {
      const host = await open();

      inDialog(host, 'Cancelar')?.click();
      TestBed.tick();

      expect(dialog(host)).toBeNull();
      expect(service.create).not.toHaveBeenCalled();
    });

    it('al crear, cierra el diálogo y vuelve a leer la lista con la vigencia nueva', async () => {
      service.create.mockResolvedValue(fiscalYear(2027));
      const host = await open();
      service.list.mockResolvedValueOnce({
        ...creatable,
        items: [fiscalYear(2027), fiscalYear(2026, 'EN_PREPARACION')],
        creatableYears: [],
        createBlocked: ALL_CREATED,
      });

      await create(host);

      expect(service.create).toHaveBeenCalledWith(2027);
      expect(service.list).toHaveBeenCalledTimes(2);
      expect(dialog(host)).toBeNull();
      expect(rows(host).map((row) => row[0])).toEqual(['2027', '2026']);
    });

    it('un V-01 vuelve a leer la lista y, sin año en curso entre los que quedan, no deja ningún año elegido', async () => {
      service.create.mockRejectedValue(conflict());
      const host = await open();
      service.list.mockResolvedValueOnce({
        ...creatable,
        items: [fiscalYear(2027), fiscalYear(2026, 'EN_PREPARACION')],
        creatableYears: [year2028],
      });

      await create(host);

      expect(service.list).toHaveBeenCalledTimes(2);
      expect(dialog(host)).not.toBeNull();
      expect(text(host.querySelector('liv-alert[role="alert"]'))).toContain(
        'V-01 · La vigencia 2027 ya existe',
      );
      expect(years(host)).toEqual(['2028']);
      expect(chosen(host)).toBeUndefined();
      expect(inDialog(host, 'Crear vigencia')?.disabled).toBe(true);
      expect(document.activeElement).toBe(host.querySelector('liv-select select'));
      expect(rows(host)).toHaveLength(2);
    });

    it('un V-01 sin años que ofrecer cierra el diálogo, deja su mensaje en la pantalla y el botón inhabilitado con su motivo', async () => {
      service.create.mockRejectedValue(conflict());
      const host = await open({ ...creatable, creatableYears: [current2027] });
      service.list.mockResolvedValueOnce({
        ...creatable,
        items: [fiscalYear(2027), fiscalYear(2026, 'EN_PREPARACION')],
        creatableYears: [],
        createBlocked: ALL_CREATED,
      });

      await create(host);

      expect(dialog(host)).toBeNull();
      expect(button(host, 'Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');
      expect(text(host)).toContain(
        'Ya existen todas las vigencias que se pueden crear: de 2002 a 2027.',
      );
      expect(text(host.querySelector('liv-alert[role="alert"]'))).toContain(
        'V-01 · La vigencia 2027 ya existe',
      );
      expect(document.activeElement).not.toBe(document.body);
    });

    it('un rechazo que no es V-01 no vuelve a leer la lista', async () => {
      service.create.mockRejectedValue(
        new HttpErrorResponse({
          status: 422,
          error: { status: 422, code: 'V-05', reason: 'PAST_LIMIT', minYear: 2002 },
        }),
      );
      const host = await open();

      await create(host);

      expect(service.list).toHaveBeenCalledTimes(1);
      expect(dialog(host)).not.toBeNull();
    });
  });

  describe('después de crear (007-04, 007-21)', () => {
    // En estas pruebas el año en curso es 2027 y el siguiente, 2028; el servidor los manda del más reciente al más antiguo.
    const current2027 = { ...NEXT_2027, relation: 'CURRENT' };
    const year2028 = { ...NEXT_2027, year: 2028, startsOn: '2028-01-01', endsOn: '2028-12-31' };
    const creatable: FiscalYearList = {
      items: [fiscalYear(2026, 'EN_PREPARACION')],
      canCreate: true,
      creatableYears: [year2028, current2027],
    };
    const dialog = (host: HTMLElement) => host.querySelector('[role="dialog"]');
    const inDialog = (host: HTMLElement, label: string) =>
      Array.from(dialog(host)?.querySelectorAll('button') ?? []).find((candidate) =>
        text(candidate).endsWith(label),
      );
    const open = async (list: FiscalYearList = creatable) => {
      const host = await render(list);
      const label = list.items.length === 0 ? 'Crear la primera vigencia' : 'Crear vigencia';
      button(host, label)?.click();
      TestBed.tick();
      return host;
    };
    const create = async (host: HTMLElement) => {
      inDialog(host, 'Crear vigencia')?.click();
      await TestBed.inject(ApplicationRef).whenStable();
      TestBed.tick();
    };
    const status = (host: HTMLElement) => text(host.querySelector('p[role="status"]'));
    const conflict = () =>
      new HttpErrorResponse({ status: 409, error: { status: 409, code: 'V-01', year: 2027 } });
    const chosen = (host: HTMLElement) =>
      host.querySelector<HTMLSelectElement>('liv-select select')?.value || undefined;

    it('anuncia en una región de estado que se creó la vigencia', async () => {
      service.create.mockResolvedValue(fiscalYear(2027));
      const host = await open();
      service.list.mockResolvedValueOnce({ ...creatable, items: [fiscalYear(2027)] });
      expect(status(host)).toBe('');

      await create(host);

      expect(status(host)).toBe('Se creó la vigencia 2027.');
    });

    it('al crear la primera vigencia, el foco pasa al enlace de su año y no se pierde', async () => {
      service.create.mockResolvedValue(fiscalYear(2026));
      const host = await open({ ...creatable, items: [] });
      service.list.mockResolvedValueOnce({ ...creatable, items: [fiscalYear(2026)] });

      await create(host);

      expect(document.activeElement).toBe(host.querySelector('tbody a'));
    });

    it('mientras se relee la lista, el diálogo sigue abierto y su acción inhabilitada', async () => {
      service.create.mockResolvedValue(fiscalYear(2027));
      const host = await open();
      let arrive: (list: FiscalYearList) => void = () => undefined;
      service.list.mockReturnValueOnce(
        new Promise<FiscalYearList>((resolve) => (arrive = resolve)),
      );

      inDialog(host, 'Crear vigencia')?.click();
      await Promise.resolve();
      await Promise.resolve();
      TestBed.tick();

      expect(dialog(host)).not.toBeNull();
      expect(inDialog(host, 'Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');

      arrive({ ...creatable, items: [fiscalYear(2027), fiscalYear(2026)] });
      await TestBed.inject(ApplicationRef).whenStable();
      TestBed.tick();
      expect(dialog(host)).toBeNull();
    });

    it('si la lista no se puede releer tras crear, conserva la que había y avisa', async () => {
      service.create.mockResolvedValue(fiscalYear(2027));
      const host = await open();
      service.list.mockRejectedValueOnce(new Error('500'));

      await create(host);

      expect(dialog(host)).toBeNull();
      expect(rows(host)).toHaveLength(1);
      expect(host.querySelector('table')).not.toBeNull();
      expect(text(host)).toContain(
        'No pudimos actualizar la lista de vigencias. Recarga la pantalla para verla al día.',
      );
      expect(text(host)).not.toContain('No pudimos cargar las vigencias');
    });

    it('si la lista no se puede releer tras un V-01, el diálogo sigue abierto con su alerta', async () => {
      service.create.mockRejectedValue(
        new HttpErrorResponse({ status: 409, error: { status: 409, code: 'V-01', year: 2027 } }),
      );
      const host = await open();
      service.list.mockRejectedValueOnce(new Error('500'));

      await create(host);

      expect(dialog(host)).not.toBeNull();
      expect(text(host.querySelector('liv-alert[role="alert"]'))).toContain('V-01');
      expect(rows(host)).toHaveLength(1);
    });

    it('mientras se relee la lista no se abre un diálogo nuevo con los años viejos', async () => {
      service.create.mockRejectedValue(conflict());
      const host = await open();
      let arrive: (list: FiscalYearList) => void = () => undefined;
      service.list.mockReturnValueOnce(
        new Promise<FiscalYearList>((resolve) => (arrive = resolve)),
      );

      inDialog(host, 'Crear vigencia')?.click();
      await Promise.resolve();
      await Promise.resolve();
      TestBed.tick();
      inDialog(host, 'Cancelar')?.click();
      TestBed.tick();
      expect(dialog(host)).toBeNull();

      button(host, 'Crear vigencia')?.click();
      TestBed.tick();
      expect(dialog(host)).toBeNull();

      arrive({
        ...creatable,
        items: [fiscalYear(2027), fiscalYear(2026)],
        creatableYears: [year2028],
      });
      await TestBed.inject(ApplicationRef).whenStable();
      TestBed.tick();
      expect(dialog(host)).toBeNull();
    });

    it('un tiempo agotado cuya relectura trae el año pedido lo da por creado: cierra y anuncia', async () => {
      service.create.mockRejectedValue(new TimeoutError(10_000));
      const host = await open();
      service.list.mockResolvedValueOnce({
        ...creatable,
        items: [fiscalYear(2027), fiscalYear(2026, 'EN_PREPARACION')],
        creatableYears: [year2028],
      });

      await create(host);

      expect(service.list).toHaveBeenCalledTimes(2);
      expect(rows(host)).toHaveLength(2);
      expect(dialog(host)).toBeNull();
      expect(status(host)).toBe('Se creó la vigencia 2027.');
    });

    it('un tiempo agotado cuya relectura no trae el año pedido deja el diálogo abierto y el año intentado elegido (H2-18)', async () => {
      service.create.mockRejectedValue(new TimeoutError(10_000));
      const host = await open();
      service.list.mockResolvedValueOnce(creatable);

      await create(host);

      expect(dialog(host)).not.toBeNull();
      expect(chosen(host)).toBe('2027');
      expect(host.querySelector<HTMLSelectElement>('liv-select select')?.disabled).toBe(false);
      expect(document.activeElement).toBe(host.querySelector('liv-select select'));
      expect(text(host.querySelector('liv-alert[role="alert"]'))).toContain(
        'No sabemos si se creó la vigencia',
      );
    });

    it('si la relectura falla tras un resultado que no se conoce, se conserva el año intentado y la lista vuelve a estar habilitada', async () => {
      service.create.mockRejectedValue(new TimeoutError(10_000));
      const host = await open();
      service.list.mockRejectedValueOnce(new Error('500'));

      await create(host);

      expect(dialog(host)).not.toBeNull();
      expect(chosen(host)).toBe('2027');
      expect(host.querySelector<HTMLSelectElement>('liv-select select')?.disabled).toBe(false);
    });

    it('si la lista no se puede releer, el diálogo lo dice dentro, donde se lee', async () => {
      service.create.mockRejectedValue(conflict());
      const host = await open();
      service.list.mockRejectedValueOnce(new Error('500'));

      await create(host);

      expect(text(dialog(host))).toContain(
        'No pudimos actualizar la lista de vigencias. Los años que se ofrecen pueden no estar al día.',
      );
    });

    it('«Cancelar» tras una relectura que quitó el botón de origen deja el foco en la tabla', async () => {
      service.create.mockRejectedValue(conflict());
      const host = await open({ ...creatable, items: [] });
      service.list.mockResolvedValueOnce({
        ...creatable,
        items: [fiscalYear(2027)],
        creatableYears: [year2028],
      });

      await create(host);
      inDialog(host, 'Cancelar')?.click();
      TestBed.tick();
      await TestBed.inject(ApplicationRef).whenStable();

      expect(dialog(host)).toBeNull();
      expect(document.activeElement).toBe(host.querySelector('tbody a'));
    });

    it('el anuncio solo usa un año entero del servidor', async () => {
      service.create.mockResolvedValue({ ...fiscalYear(2027), year: '2027' as unknown as number });
      const host = await open();

      await create(host);

      expect(status(host)).toBe('Se creó la vigencia.');
    });
  });
});
