import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  BadgeComponent,
  type BadgeTone,
  ButtonComponent,
  formatBusinessDate,
  PageHeaderComponent,
  ResultStateComponent,
  SkeletonComponent,
  TableCellComponent,
  TableComponent,
  TableRowComponent,
} from 'liv-components';

import type { CreatableYear, FiscalYear, FiscalYearList } from '../../api/tax/models';
import { OPTION_ROUTE } from '../../core/app-paths';
import { CreateFiscalYearDialog } from './create-fiscal-year-dialog';
import { FiscalYearsService } from './fiscal-years.service';

/** Cómo se muestra cada estado de una vigencia (Doc. 01 §5); uno que no conoce se muestra tal cual. */
const STATUSES: Readonly<Record<string, { label: string; tone: BadgeTone }>> = {
  PLANEADA: { label: 'Planeada', tone: 'neutral' },
  EN_PREPARACION: { label: 'En preparación', tone: 'primary' },
  LISTA: { label: 'Lista', tone: 'info' },
  HABILITADA: { label: 'Habilitada', tone: 'success' },
  CERRADA: { label: 'Cerrada', tone: 'neutral' },
};

const NO_YEARS_AVAILABLE = 'No hay años disponibles para crear una vigencia.';

/** Cuántas filas de marcadores se dibujan mientras carga (las del diseño). */
const SKELETON_ROWS = [1, 2, 3];

type View = 'loading' | 'ready' | 'error';

/** Una fila de la tabla, lista para mostrar: textos ya formateados y el destino de su enlace. */
interface Row {
  readonly year: number;
  /** El destino del enlace del año; `undefined` si el año no es un entero, y entonces se muestra como texto. */
  readonly href: string | undefined;
  readonly startsOn: string;
  readonly endsOn: string;
  readonly status: string;
  readonly tone: BadgeTone;
  readonly liquidation: string;
}

/**
 * Vigencias fiscales, opción 1.4 (007-01, 007-03, 007-09): las vigencias de la administración y, si la
 * persona puede crearlas, la acción para hacerlo. Todo lo que decide qué se ofrece (`canCreate`,
 * `creatableYears`, `createBlocked`) viene del backend; la pantalla no calcula años ni usa el reloj. Diseño:
 * Figma LIV-Diseño, `03 Pantallas`, sección 1.4 (nodo 66:1966): lista 66:1967, consulta 66:2250, sin años
 * disponibles 66:2513, vacía 66:2793 y 66:3041, cargando 66:3271 y error 66:3493.
 */
@Component({
  selector: 'app-fiscal-years',
  imports: [
    PageHeaderComponent,
    ButtonComponent,
    TableComponent,
    TableRowComponent,
    TableCellComponent,
    BadgeComponent,
    ResultStateComponent,
    SkeletonComponent,
    CreateFiscalYearDialog,
  ],
  templateUrl: './fiscal-years.html',
  styleUrl: './fiscal-years.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiscalYears {
  private readonly router = inject(Router);
  private readonly fiscalYears = inject(FiscalYearsService);

  protected readonly breadcrumb = ['Administración', '1.4 Vigencias fiscales'];
  protected readonly skeletonRows = SKELETON_ROWS;

  protected readonly view = signal<View>('loading');
  private readonly list = signal<FiscalYearList | undefined>(undefined);
  /** Número de la última lectura pedida: la respuesta de una anterior no pisa la de una más nueva. */
  private latestRead = 0;

  protected readonly rows = computed<Row[]>(() =>
    (this.list()?.items ?? []).map((item) => this.toRow(item)),
  );
  protected readonly creatableYears = computed(() => this.list()?.creatableYears ?? []);
  /** El diálogo de creación está abierto. */
  protected readonly creating = signal(false);
  protected readonly isEmpty = computed(() => this.rows().length === 0);
  protected readonly canCreate = computed(() => this.list()?.canCreate === true);

  /**
   * Por qué no se puede crear, con permiso y cuando el backend bloquea la creación o no ofrece ningún año; sin
   * motivo, la acción está activa. El año del motivo solo se escribe si es un entero (`Number.isInteger`): si
   * no, cae en el texto general.
   */
  protected readonly blockedReason = computed(() => {
    const list = this.list();
    if (!list?.canCreate) {
      return undefined;
    }
    const blocked = list.createBlocked;
    if (blocked) {
      return blocked.reason === 'FUTURE_LIMIT' && Number.isInteger(blocked.maxYear)
        ? `Ya existe la vigencia ${blocked.maxYear}, la última que se puede crear.`
        : NO_YEARS_AVAILABLE;
    }
    return list.creatableYears.length === 0 ? NO_YEARS_AVAILABLE : undefined;
  });

  /** La acción de crear queda inhabilitada cuando el backend dice que no hay años disponibles. */
  protected readonly createDisabled = computed(() => this.blockedReason() !== undefined);

  protected readonly emptyDescription = computed(() => {
    if (!this.canCreate()) {
      return 'Tu administración todavía no tiene vigencias fiscales. Solo el administrador del sistema puede crearlas.';
    }
    const blocked = this.blockedReason();
    if (blocked) {
      return blocked;
    }
    const options = (this.list()?.creatableYears ?? []).map((candidate) =>
      describeCreatableYear(candidate),
    );
    return options.length > 0
      ? `Crea la primera vigencia de tu administración: puede ser ${options.join(' o ')}.`
      : 'Crea la primera vigencia de tu administración.';
  });

  constructor() {
    void this.load();
  }

  /** Lee las vigencias y muestra «cargando» mientras tanto. */
  protected load(): Promise<boolean> {
    this.view.set('loading');
    return this.read();
  }

  /** Vuelve a leer las vigencias sin mostrar «cargando»: la lista sigue a la vista hasta que llega la nueva. */
  private reload(): Promise<boolean> {
    return this.read();
  }

  /** Lee las vigencias; dice si salió bien. La respuesta de una lectura anterior a la última no cuenta. */
  private async read(): Promise<boolean> {
    const read = ++this.latestRead;
    try {
      const list = await this.fiscalYears.list();
      if (read === this.latestRead) {
        this.list.set(list);
        this.view.set('ready');
      }
      return true;
    } catch {
      if (read === this.latestRead) {
        this.view.set('error');
      }
      return false;
    }
  }

  /** La vigencia quedó creada: se cierra el diálogo y la lista se vuelve a leer con ella. */
  protected async onCreated(): Promise<void> {
    this.creating.set(false);
    await this.reload();
  }

  /**
   * Otra persona creó el año antes (V-01): se vuelve a leer la lista. El diálogo sigue abierto, con el primer
   * año que quede disponible; si no queda ninguno (o la lista no se pudo leer), se cierra.
   */
  protected async onConflict(): Promise<void> {
    const read = await this.reload();
    if (!read || this.creatableYears().length === 0) {
      this.creating.set(false);
    }
  }

  protected open(href: string): void {
    void this.router.navigateByUrl(href);
  }

  private toRow(item: FiscalYear): Row {
    const known = Object.hasOwn(STATUSES, item.status) ? STATUSES[item.status] : undefined;
    return {
      year: item.year,
      // Solo un entero forma una ruta: lo demás se muestra como texto, sin enlace.
      href: Number.isInteger(item.year) ? `/${OPTION_ROUTE}/1.4/${item.year}` : undefined,
      // Fecha larga explícita: en la lista de vigencias manda el diseño (Figma 66:1967, H2-13).
      startsOn: formatBusinessDate(item.startsOn, 'long') ?? 'Sin fecha',
      endsOn: formatBusinessDate(item.endsOn, 'long') ?? 'Sin fecha',
      status: known?.label ?? item.status,
      tone: known?.tone ?? 'neutral',
      liquidation: item.assessmentsEnabled ? 'Habilitada' : 'No habilitada',
    };
  }
}

/** Cómo se nombra un año que se puede crear: la relación y el año salen de lo que manda el backend. */
function describeCreatableYear(candidate: CreatableYear): string {
  switch (candidate.relation) {
    case 'CURRENT':
      return `la del año en curso (${candidate.year})`;
    case 'NEXT':
      return `la del año siguiente (${candidate.year})`;
    case 'PAST':
      return `la de un año pasado (${candidate.year})`;
    default:
      return `la de ${candidate.year}`;
  }
}
