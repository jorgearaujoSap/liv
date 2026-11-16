import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import {
  AlertComponent,
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

/** Cómo terminó una lectura: aplicada, fallida o descartada por una más nueva. */
type ReadResult = 'applied' | 'failed' | 'stale';

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
    AlertComponent,
    CreateFiscalYearDialog,
  ],
  templateUrl: './fiscal-years.html',
  styleUrl: './fiscal-years.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiscalYears {
  private readonly router = inject(Router);
  private readonly fiscalYears = inject(FiscalYearsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

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
  /** Se está releyendo la lista sin mostrar «cargando»: mientras tanto no se ofrece crear. */
  protected readonly reloading = signal(false);
  /** La última relectura falló: la lista que se ve puede estar vieja. */
  protected readonly refreshFailed = signal(false);
  /** Lo que se anuncia a los lectores de pantalla tras una creación; la región existe desde el inicio. */
  protected readonly notice = signal('');
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
  protected readonly createDisabled = computed(
    () => this.blockedReason() !== undefined || this.reloading(),
  );

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
  protected load(): Promise<ReadResult> {
    this.view.set('loading');
    return this.read(false);
  }

  /**
   * Vuelve a leer las vigencias sin mostrar «cargando»: la lista sigue a la vista hasta que llega la nueva, y
   * si la lectura falla se conserva y se avisa, porque lo que hay a la vista sigue siendo lo último que se supo.
   */
  private async reload(): Promise<ReadResult> {
    this.reloading.set(true);
    this.refreshFailed.set(false);
    const result = await this.read(true);
    if (result === 'failed') {
      this.refreshFailed.set(true);
    }
    return result;
  }

  /**
   * Lee las vigencias. La respuesta de una lectura anterior a la última se descarta (`stale`); por eso
   * `reloading` lo baja la lectura que sí cuenta, y no `reload()`: una descartada no debe apagarlo.
   */
  private async read(silent: boolean): Promise<ReadResult> {
    const read = ++this.latestRead;
    try {
      const list = await this.fiscalYears.list();
      if (read !== this.latestRead) {
        return 'stale';
      }
      this.list.set(list);
      this.view.set('ready');
      this.reloading.set(false);
      return 'applied';
    } catch {
      if (read !== this.latestRead) {
        return 'stale';
      }
      if (!silent) {
        this.view.set('error');
      }
      this.reloading.set(false);
      return 'failed';
    }
  }

  /**
   * La vigencia quedó creada: se vuelve a leer la lista con ella, con el diálogo abierto y su acción ocupada
   * para no ofrecer años que ya no están, y después se cierra y se anuncia. Si la lista no se puede releer,
   * se conserva la que había y se avisa.
   */
  protected async onCreated(created: FiscalYear): Promise<void> {
    await this.reload();
    this.creating.set(false);
    this.notice.set(`Se creó la vigencia ${created.year}.`);
    this.focusFirstYearIfLost();
  }

  /**
   * La lista que se muestra pudo quedar vieja (V-01, o no se sabe si se creó): se vuelve a leer. El diálogo
   * sigue abierto, con el primer año que quede disponible; solo se cierra si ya no queda ninguno. Si la lista
   * no se pudo leer, el diálogo sigue abierto con su alerta.
   */
  protected async onStaleList(): Promise<void> {
    const result = await this.reload();
    if (result === 'applied' && this.creatableYears().length === 0) {
      this.creating.set(false);
    }
  }

  protected open(href: string): void {
    void this.router.navigateByUrl(href);
  }

  /**
   * Al cerrarse el diálogo, el foco vuelve a quien lo abrió. Si esa acción ya no existe (la primera vigencia
   * quita el estado vacío), el foco se perdería en el documento: pasa al enlace de la primera vigencia.
   */
  private focusFirstYearIfLost(): void {
    afterNextRender(
      () => {
        const root = this.host.nativeElement;
        const active = root.ownerDocument.activeElement;
        if (!active || !root.contains(active) || active === root.ownerDocument.body) {
          root.querySelector<HTMLElement>('tbody a[href]')?.focus();
        }
      },
      { injector: this.injector },
    );
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
