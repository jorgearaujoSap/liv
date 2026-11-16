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

import type { FiscalYear, FiscalYearList } from '../../api/tax/models';
import { OPTION_ROUTE } from '../../core/app-paths';
import { CreateFiscalYearDialog, type StaleList } from './create-fiscal-year-dialog';
import { asYear, type Rejection } from './fiscal-year-rejections';
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
  /** El rechazo que dejó al diálogo sin años que ofrecer: se cierra y su mensaje queda en la pantalla. */
  protected readonly pageRejection = signal<Rejection | undefined>(undefined);
  /** Lo que se anuncia a los lectores de pantalla tras una creación; la región existe desde el inicio. */
  protected readonly notice = signal('');
  protected readonly isEmpty = computed(() => this.rows().length === 0);
  protected readonly canCreate = computed(() => this.list()?.canCreate === true);

  /**
   * Por qué no se puede crear, con permiso y cuando el backend bloquea la creación o no ofrece ningún año; sin
   * motivo, la acción está activa. Los años del motivo solo se escriben si son años de la tabla nacional y van en
   * orden (`asYear`): si no, cae en el texto general.
   */
  protected readonly blockedReason = computed(() => {
    const list = this.list();
    if (!list?.canCreate) {
      return undefined;
    }
    const blocked = list.createBlocked;
    if (blocked) {
      return allCreatedReason(blocked) ?? NO_YEARS_AVAILABLE;
    }
    return this.creatableYears().length === 0 ? NO_YEARS_AVAILABLE : undefined;
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
    const range = yearRange(this.creatableYears().map((candidate) => candidate.year));
    if (!range) {
      return 'Crea la primera vigencia de tu administración.';
    }
    return range.first === range.last
      ? `Crea la primera vigencia de tu administración. Puedes crear el año ${range.first}.`
      : `Crea la primera vigencia de tu administración. Puedes crear cualquier año de ${range.first} a ${range.last}.`;
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

  /** Abre el diálogo. No se abre mientras se relee la lista: ofrecería años que pueden ya no estar. */
  protected startCreating(): void {
    if (this.reloading()) {
      return;
    }
    this.pageRejection.set(undefined);
    this.creating.set(true);
  }

  /**
   * La vigencia quedó creada: se vuelve a leer la lista con ella, con el diálogo abierto y su acción ocupada
   * para no ofrecer años que ya no están, y después se cierra y se anuncia. Si la lista no se puede releer,
   * se conserva la que había y se avisa.
   */
  protected async onCreated(created: FiscalYear): Promise<void> {
    await this.reload();
    this.closeDialog();
    this.announceCreated(created.year);
  }

  /**
   * La lista que se muestra pudo quedar vieja (V-01, o no se sabe si se creó): se vuelve a leer.
   * - Si no se sabía el resultado y el año ya está en la lista, se creó: se cierra y se anuncia.
   * - Si ya no queda ningún año que ofrecer, se cierra y el mensaje del rechazo queda en la pantalla.
   * - Si no, el diálogo sigue abierto, con el primer año que siga disponible (el plan lo pide así).
   * Si la lista no se pudo leer, el diálogo sigue abierto con su alerta y lo dice.
   */
  protected async onStaleList(stale: StaleList): Promise<void> {
    this.pageRejection.set(undefined);
    const result = await this.reload();
    if (result !== 'applied') {
      return;
    }
    if (stale.unknown && this.rows().some((row) => row.year === stale.year)) {
      this.closeDialog();
      this.announceCreated(stale.year);
    } else if (this.creatableYears().length === 0) {
      this.pageRejection.set(stale.rejection);
      this.closeDialog();
    }
  }

  /** Cierra el diálogo y cuida que el foco no se pierda. */
  protected closeDialog(): void {
    this.creating.set(false);
    this.focusFirstYearIfLost();
  }

  /** Solo se anuncia un año entero: lo que mande el servidor no se escribe sin validar. */
  private announceCreated(year: number): void {
    this.notice.set(
      Number.isInteger(year) ? `Se creó la vigencia ${year}.` : 'Se creó la vigencia.',
    );
  }

  protected open(href: string): void {
    void this.router.navigateByUrl(href);
  }

  /**
   * Al cerrarse el diálogo, el foco vuelve a quien lo abrió. Si esa acción ya no existe (la primera vigencia
   * quita el estado vacío, o la relectura cambió lo que se ofrece), el foco se perdería en el documento: pasa al
   * enlace de la primera vigencia o, sin tabla, al primer botón.
   */
  private focusFirstYearIfLost(): void {
    afterNextRender(
      () => {
        const root = this.host.nativeElement;
        const active = root.ownerDocument.activeElement;
        if (!active || !root.contains(active) || active === root.ownerDocument.body) {
          const target =
            root.querySelector<HTMLElement>('tbody a[href]') ??
            root.querySelector<HTMLElement>('button');
          target?.focus();
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
      // Una tabla de consulta: dd/mm/aaaa (decisión de la H3, constitución y arquitectura 01 §11).
      startsOn: formatBusinessDate(item.startsOn, 'short') ?? 'Sin fecha',
      endsOn: formatBusinessDate(item.endsOn, 'short') ?? 'Sin fecha',
      status: known?.label ?? item.status,
      tone: known?.tone ?? 'neutral',
      liquidation: item.assessmentsEnabled ? 'Habilitada' : 'No habilitada',
    };
  }
}

/**
 * El primer y el último año de los que manda el servidor, sin depender del orden de la lista; `undefined` si no hay
 * ninguno o si alguno no es un año de la tabla nacional (`asYear`): entonces la pantalla no escribe ningún rango.
 */
function yearRange(years: readonly unknown[]): { first: number; last: number } | undefined {
  const valid = years.map((year) => asYear(year));
  if (valid.length === 0 || valid.includes(undefined)) {
    return undefined;
  }
  const numbers = valid as number[];
  return { first: Math.min(...numbers), last: Math.max(...numbers) };
}

/**
 * El motivo de `ALL_CREATED` con su rango (007-23), o `undefined` si el motivo es otro o los años no son años de
 * la tabla nacional y van en orden: entonces la pantalla usa el texto general.
 */
function allCreatedReason(
  blocked: NonNullable<FiscalYearList['createBlocked']>,
): string | undefined {
  const min = asYear(blocked.minYear);
  const max = asYear(blocked.maxYear);
  if (blocked.reason !== 'ALL_CREATED' || min === undefined || max === undefined || min > max) {
    return undefined;
  }
  return `Ya existen todas las vigencias que se pueden crear: de ${min} a ${max}.`;
}
