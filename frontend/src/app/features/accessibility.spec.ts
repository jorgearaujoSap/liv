import { HttpErrorResponse } from '@angular/common/http';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import axe from 'axe-core';
import { vi } from 'vitest';

import type { FiscalYear, FiscalYearDetail, FiscalYearList } from '../api/tax/models';
import { buildMenu } from '../core/navigation/menu-presentation';
import { IdleService, type SessionWarning } from '../core/session/idle.service';
import { ComingSoon } from './errors/coming-soon';
import { Forbidden } from './errors/forbidden';
import { LoadError } from './errors/load-error';
import { FiscalYearDetailPage } from './fiscal-years/fiscal-year-detail';
import { FiscalYears } from './fiscal-years/fiscal-years';
import { FiscalYearsService } from './fiscal-years/fiscal-years.service';
import { Home } from './home/home';
import { Shell } from './shell/shell';
import { WorkspaceService } from './shell/workspace.service';
import { FakeWorkspace, leaf } from './testing/fixtures';

/**
 * Ejecuta axe-core sobre la pantalla. En jsdom no se puede calcular el contraste de color, así que esta
 * prueba no lo cubre: lo cubren los *tokens* de `liv-components` (contraste verificado en la librería) y,
 * sobre el navegador real, el e2e de T23.
 */
async function violations(element: Element): Promise<string[]> {
  const results = await axe.run(element, { rules: { 'color-contrast': { enabled: false } } });
  return results.violations.map(
    (violation) =>
      `${violation.id}: ${violation.help} (${violation.nodes.map((node) => node.target).join(' | ')})`,
  );
}

const fiscalYear = (year: number, status: FiscalYear['status']): FiscalYear => ({
  year,
  startsOn: `${year}-01-01`,
  endsOn: `${year}-12-31`,
  status,
  assessmentsEnabled: false,
});

/** Los nueve puntos de la lista de preparación: el 1 cumplido y los demás pendientes («Opción aún no disponible»). */
const preparationItems = (planned: boolean): FiscalYearDetail['preparation']['items'] =>
  [
    ['FISCAL_YEAR_IN_PREPARATION', true],
    ['VALUATION_RESOLUTION', true],
    ['VALUATION_CATALOG_PUBLISHED', true],
    ['TAX_RATES', true],
    ['TAX_CALENDAR', true],
    ['TAX_BENEFITS', false],
    ['PENALTY_RULES', false],
    ['SAMPLE_ASSESSMENT', true],
    ['JURISDICTION_CONFIG_PUBLISHED', true],
  ].map(([code, required], index) => ({
    code: code as string,
    number: index + 1,
    option: `1.5.${index}`,
    required: required as boolean,
    status: index === 0 && !planned ? 'MET' : 'PENDING',
    reason: index === 0 ? (planned ? 'FISCAL_YEAR_PLANNED' : 'MET') : 'OPTION_NOT_AVAILABLE',
  }));

const fiscalYearDetail = (year: number, planned: boolean): FiscalYearDetail => ({
  year,
  startsOn: `${year}-01-01`,
  endsOn: `${year}-12-31`,
  status: planned ? 'PLANEADA' : 'EN_PREPARACION',
  assessmentsEnabled: false,
  availableActions: planned ? ['OPEN_PREPARATION'] : [],
  preparation: {
    items: preparationItems(planned),
    missing: planned ? [1, 2, 3, 4, 5, 8, 9] : [2, 3, 4, 5, 8, 9],
    canEnable: false,
  },
});

class FakeFiscalYears {
  readonly list = vi.fn<() => Promise<FiscalYearList>>();
  readonly create = vi.fn<(year: number) => Promise<FiscalYear>>();
  readonly get = vi.fn<(year: number) => Promise<FiscalYearDetail>>();
  readonly openPreparation = vi.fn<(year: number) => Promise<FiscalYear>>();
}

class FakeIdle {
  readonly warning = signal<SessionWarning | undefined>(undefined);
  readonly extend = vi.fn().mockResolvedValue(true);
  readonly logout = vi.fn().mockResolvedValue(undefined);
}

// 001-19: las pantallas de la aplicación cumplen WCAG 2.1 AA (axe-core).
// axe recorre todo el DOM de la estructura general, y en jsdom tarda: más de los 5 s predeterminados.
describe('accesibilidad de las pantallas (001-19)', { timeout: 60_000 }, () => {
  let workspace: FakeWorkspace;
  let idle: FakeIdle;
  let fiscalYears: FakeFiscalYears;

  const open = async (url: string) => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => {
    workspace = new FakeWorkspace();
    idle = new FakeIdle();
    fiscalYears = new FakeFiscalYears();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: Shell,
            children: [
              { path: '', pathMatch: 'full', component: Home },
              { path: 'sin-permiso', component: Forbidden },
              { path: 'proximamente', component: ComingSoon },
              { path: 'opcion/1.4', component: FiscalYears },
              { path: 'opcion/1.4/:year', component: FiscalYearDetailPage },
            ],
          },
          { path: 'error', component: LoadError },
        ]),
        { provide: WorkspaceService, useValue: workspace },
        { provide: IdleService, useValue: idle },
        { provide: FiscalYearsService, useValue: fiscalYears },
      ],
    });
  });

  it('Inicio', async () => {
    const page = await open('/');

    expect(page.querySelector('h1')?.textContent).toContain('Hola, Laura');
    expect(await violations(page)).toEqual([]);
  });

  it('Inicio con el menú abierto en un módulo con subopciones', async () => {
    const page = await open('/');
    const group = page.querySelector('liv-nav-item[data-type="group"]') as HTMLElement;
    (group.querySelector('button') as HTMLButtonElement).click();
    TestBed.tick();

    expect(group.querySelector('button')?.getAttribute('aria-expanded')).toBe('true');
    expect(group.querySelectorAll('liv-nav-item[data-type="subitem"]').length).toBeGreaterThan(0);
    expect(await violations(page)).toEqual([]);
  });

  it('Inicio con opciones disponibles (enlaces) y sin construir', async () => {
    workspace.menu.set(
      buildMenu({
        items: [
          { code: '1', status: 'AVAILABLE', children: [leaf('1.2', 'AVAILABLE'), leaf('1.4')] },
          leaf('3', 'AVAILABLE'),
          leaf('4'),
        ],
      }),
    );
    const page = await open('/');
    (page.querySelector('liv-nav-item[data-type="group"] button') as HTMLButtonElement).click();
    TestBed.tick();

    expect(page.querySelectorAll('a[href^="/opcion/"]').length).toBeGreaterThan(2);
    expect(await violations(page)).toEqual([]);
  });

  it('Menú de tres niveles abierto (1.5.x)', async () => {
    workspace.menu.set(
      buildMenu({
        items: [
          {
            code: '1',
            status: 'AVAILABLE',
            children: [
              leaf('1.4', 'AVAILABLE'),
              {
                code: '1.5',
                status: 'AVAILABLE',
                children: [leaf('1.5.3', 'AVAILABLE'), leaf('1.5.4')],
              },
              { code: '1.6', status: 'COMING_SOON', children: [leaf('1.6.1')] },
            ],
          },
        ],
      }),
    );
    const page = await open('/');
    (page.querySelector('liv-nav-item[data-type="group"] button') as HTMLButtonElement).click();
    TestBed.tick();
    (page.querySelector('liv-nav-item[data-type="subgroup"] button') as HTMLButtonElement).click();
    TestBed.tick();

    expect(page.querySelectorAll('liv-nav-item[data-type="nested"]')).toHaveLength(2);
    expect(await violations(page)).toEqual([]);
  });

  it('Aviso de sesión por expirar', async () => {
    const page = await open('/');
    idle.warning.set({ kind: 'idle', secondsLeft: 120 });
    TestBed.tick();

    expect(page.querySelector('[role="dialog"]')).not.toBeNull();
    expect(await violations(page)).toEqual([]);
  });

  it('Aviso del límite de 8 horas', async () => {
    const page = await open('/');
    idle.warning.set({ kind: 'absolute', secondsLeft: 300 });
    TestBed.tick();

    expect(page.querySelector('[role="dialog"]')).not.toBeNull();
    expect(await violations(page)).toEqual([]);
  });

  it('Sin permiso', async () => {
    const page = await open('/sin-permiso');

    expect(page.querySelector('h1')?.textContent).toContain('No tienes permiso');
    expect(await violations(page)).toEqual([]);
  });

  it('Próximamente', async () => {
    const page = await open('/proximamente');

    expect(page.querySelector('h1')?.textContent).toContain('disponible pronto');
    expect(await violations(page)).toEqual([]);
  });

  it('Cargando', async () => {
    workspace.loading = new Promise<void>(() => undefined);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    harness.detectChanges();
    const page = harness.fixture.nativeElement as HTMLElement;

    expect(page.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(await violations(page)).toEqual([]);
  });

  it('Error', async () => {
    const page = await open('/error');

    expect(page.querySelector('h1')?.textContent).toContain('No pudimos cargar tu sesión');
    expect(await violations(page)).toEqual([]);
  });

  // 007-21: las pantallas de la opción 1.4 pasan axe.
  describe('Vigencias fiscales (007-21)', () => {
    it('con vigencias y el botón inhabilitado con su motivo', async () => {
      fiscalYears.list.mockResolvedValue({
        items: [fiscalYear(2027, 'PLANEADA'), fiscalYear(2026, 'EN_PREPARACION')],
        canCreate: true,
        creatableYears: [],
        createBlocked: { reason: 'ALL_CREATED', minYear: 2002, maxYear: 2027 },
      });
      const page = await open('/opcion/1.4');

      expect(page.querySelector('h1')?.textContent).toContain('Vigencias fiscales');
      expect(page.textContent).toContain(
        'Ya existen todas las vigencias que se pueden crear: de 2002 a 2027.',
      );
      expect(page.querySelector('button[aria-disabled="true"][aria-describedby]')).not.toBeNull();
      expect(await violations(page)).toEqual([]);
    });

    it('con «Crear vigencia» habilitado', async () => {
      fiscalYears.list.mockResolvedValue({
        items: [fiscalYear(2026, 'EN_PREPARACION')],
        canCreate: true,
        creatableYears: [
          { year: 2027, startsOn: '2027-01-01', endsOn: '2027-12-31', relation: 'NEXT' },
        ],
      });
      const page = await open('/opcion/1.4');

      expect(page.querySelectorAll('tbody tr')).toHaveLength(1);
      expect(await violations(page)).toEqual([]);
    });

    it('vacía, para quien puede crear', async () => {
      fiscalYears.list.mockResolvedValue({
        items: [],
        canCreate: true,
        creatableYears: [
          { year: 2026, startsOn: '2026-01-01', endsOn: '2026-12-31', relation: 'CURRENT' },
        ],
      });
      const page = await open('/opcion/1.4');

      expect(page.textContent).toContain('Aún no hay vigencias');
      expect(await violations(page)).toEqual([]);
    });

    it('vacía, solo de consulta', async () => {
      fiscalYears.list.mockResolvedValue({ items: [], canCreate: false, creatableYears: [] });
      const page = await open('/opcion/1.4');

      expect(page.textContent).toContain('Aún no hay vigencias');
      expect(await violations(page)).toEqual([]);
    });

    it('cargando', async () => {
      fiscalYears.list.mockReturnValue(new Promise<FiscalYearList>(() => undefined));
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/opcion/1.4');
      harness.detectChanges();
      const page = harness.fixture.nativeElement as HTMLElement;

      expect(page.querySelector('table[aria-busy="true"]')).not.toBeNull();
      expect(await violations(page)).toEqual([]);
    });

    it('con error', async () => {
      fiscalYears.list.mockRejectedValue(new Error('500'));
      const page = await open('/opcion/1.4');

      expect(page.textContent).toContain('No pudimos cargar las vigencias');
      expect(await violations(page)).toEqual([]);
    });

    describe('con el diálogo de creación abierto', () => {
      // En estas pruebas el año en curso es 2027: el servidor manda del más reciente al más antiguo.
      const creatableYear = (year: number, relation: string) => ({
        year,
        startsOn: `${year}-01-01`,
        endsOn: `${year}-12-31`,
        relation,
      });
      const withCreatableYears: FiscalYearList = {
        items: [fiscalYear(2026, 'EN_PREPARACION')],
        canCreate: true,
        creatableYears: [
          creatableYear(2028, 'NEXT'),
          creatableYear(2027, 'CURRENT'),
          creatableYear(2025, 'PAST'),
        ],
      };
      const withoutCurrentYear: FiscalYearList = {
        ...withCreatableYears,
        creatableYears: [creatableYear(2028, 'NEXT'), creatableYear(2025, 'PAST')],
      };
      const choose = (page: HTMLElement, year: number) => {
        const select = page.querySelector('[role="dialog"] select') as HTMLSelectElement;
        select.value = String(year);
        select.dispatchEvent(new Event('change', { bubbles: true }));
        TestBed.tick();
      };
      const press = (page: HTMLElement, selector: string, label: string) =>
        (
          Array.from(page.querySelectorAll(selector)).find(
            (candidate) => candidate.textContent?.trim() === label,
          ) as HTMLButtonElement
        ).click();
      const openDialog = async (list: FiscalYearList = withCreatableYears) => {
        fiscalYears.list.mockResolvedValue(list);
        const page = await open('/opcion/1.4');
        press(page, 'button', 'Crear vigencia');
        TestBed.tick();
        return page;
      };

      it('con el año en curso elegido, la advertencia y las fechas', async () => {
        const page = await openDialog();

        const select = page.querySelector<HTMLSelectElement>('[role="dialog"] select');
        expect(select?.value).toBe('2027');
        expect(page.querySelector('[role="dialog"] dl')).not.toBeNull();
        expect(await violations(page)).toEqual([]);
      });

      it('sin año elegido, con «Elige un año» y «Crear vigencia» inhabilitado', async () => {
        const page = await openDialog(withoutCurrentYear);

        const dialog = page.querySelector('[role="dialog"]');
        expect(dialog?.querySelector<HTMLSelectElement>('select')?.value).toBe('');
        expect(dialog?.querySelector('dl')).toBeNull();
        const create = Array.from(dialog?.querySelectorAll('button') ?? []).find((candidate) =>
          candidate.textContent?.trim().endsWith('Crear vigencia'),
        );
        expect(create?.disabled).toBe(true);
        expect(await violations(page)).toEqual([]);
      });

      it('con un año pasado elegido y su advertencia', async () => {
        const page = await openDialog();
        choose(page, 2025);

        expect(page.querySelector('[role="dialog"]')?.textContent).toContain('Es un año pasado');
        expect(await violations(page)).toEqual([]);
      });

      it('con un rechazo del servidor', async () => {
        fiscalYears.create.mockRejectedValue(
          new HttpErrorResponse({ status: 409, error: { status: 409, code: 'V-01', year: 2027 } }),
        );
        const page = await openDialog();
        press(page, '[role="dialog"] button', 'Crear vigencia');
        await TestBed.inject(ApplicationRef).whenStable();
        TestBed.tick();

        expect(page.querySelector('liv-alert[role="alert"]')).not.toBeNull();
        expect(await violations(page)).toEqual([]);
      });
    });
  });

  // 007-21: el detalle de la vigencia y su lista de preparación pasan axe.
  describe('Detalle de la vigencia (007-21)', () => {
    it('En preparación, con «Habilitar liquidación» inhabilitado con su motivo', async () => {
      fiscalYears.get.mockResolvedValue(fiscalYearDetail(2026, false));
      const page = await open('/opcion/1.4/2026');

      expect(page.querySelector('h1')?.textContent).toContain('Vigencia 2026');
      expect(page.querySelectorAll('[livChecklistItem]')).toHaveLength(9);
      expect(page.querySelector('button[aria-disabled="true"][aria-describedby]')).not.toBeNull();
      expect(await violations(page)).toEqual([]);
    });

    it('Planeada, con «Abrir preparación»', async () => {
      fiscalYears.get.mockResolvedValue(fiscalYearDetail(2027, true));
      const page = await open('/opcion/1.4/2027');

      expect(page.textContent).toContain('Abrir preparación');
      expect(await violations(page)).toEqual([]);
    });

    it('con el rechazo V-06 encima de los datos', async () => {
      fiscalYears.get.mockResolvedValue(fiscalYearDetail(2027, true));
      fiscalYears.openPreparation.mockRejectedValue(
        new HttpErrorResponse({ status: 409, error: { status: 409, code: 'V-06', year: 2027 } }),
      );
      const page = await open('/opcion/1.4/2027');
      (
        Array.from(page.querySelectorAll('button')).find(
          (candidate) => candidate.textContent?.trim() === 'Abrir preparación',
        ) as HTMLButtonElement
      ).click();
      await TestBed.inject(ApplicationRef).whenStable();
      TestBed.tick();

      expect(page.querySelector('liv-alert[role="alert"]')).not.toBeNull();
      expect(await violations(page)).toEqual([]);
    });

    it('no encontrada', async () => {
      const page = await open('/opcion/1.4/abc');

      expect(page.textContent).toContain('No encontramos esa vigencia');
      expect(await violations(page)).toEqual([]);
    });

    it('con error', async () => {
      fiscalYears.get.mockRejectedValue(new Error('500'));
      const page = await open('/opcion/1.4/2026');

      expect(page.textContent).toContain('No pudimos cargar la vigencia');
      expect(await violations(page)).toEqual([]);
    });

    it('cargando', async () => {
      fiscalYears.get.mockReturnValue(new Promise<FiscalYearDetail>(() => undefined));
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/opcion/1.4/2026');
      harness.detectChanges();
      const page = harness.fixture.nativeElement as HTMLElement;

      expect(page.querySelector('[aria-busy="true"]')).not.toBeNull();
      // Panel no deja fijar su nombre accesible aparte del título (0.4.0): dos paneles que se llamaran igual
      // («Cargando…», como en Figma) fallan `landmark-unique`. Cada panel de «cargando» lleva su propio título.
      const names = Array.from(page.querySelectorAll('[aria-busy="true"] liv-panel')).map((panel) =>
        panel.querySelector('h2, h3')?.textContent?.trim(),
      );
      expect(names).toHaveLength(2);
      expect(names.every((name) => !!name)).toBe(true);
      expect(new Set(names).size).toBe(2);
      expect(await violations(page)).toEqual([]);
    });
  });
});
