import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  AlertComponent,
  type BadgeTone,
  ButtonComponent,
  ChecklistItemComponent,
  type ChecklistItemStatus,
  DescriptionItemComponent,
  formatBusinessDate,
  PageHeaderComponent,
  PanelComponent,
  ResultStateComponent,
  SkeletonComponent,
} from 'liv-components';

import type { FiscalYearDetail, PreparationItem } from '../../api/tax/models';
import { OPTION_ROUTE } from '../../core/app-paths';
import { hasStatus } from '../../core/http/http-errors';
import { openPreparationRejection } from './fiscal-year-rejections';
import { FiscalYearsService } from './fiscal-years.service';

/** Los años de la tabla nacional de vigencias (el rango del contrato `tax`). */
const MIN_YEAR = 1999;
const MAX_YEAR = 2100;

/** La acción de `availableActions` que esta pantalla conoce; las demás se ignoran. */
const OPEN_PREPARATION = 'OPEN_PREPARATION';

/** Cómo se muestra cada estado de una vigencia (Doc. 01 §5); uno que no conoce se muestra tal cual. */
const STATUSES: Readonly<Record<string, { label: string; tone: BadgeTone }>> = {
  PLANEADA: { label: 'Planeada', tone: 'neutral' },
  EN_PREPARACION: { label: 'En preparación', tone: 'primary' },
  LISTA: { label: 'Lista', tone: 'info' },
  HABILITADA: { label: 'Habilitada', tone: 'success' },
  CERRADA: { label: 'Cerrada', tone: 'neutral' },
};

/** El título de cada punto de la lista (Doc. 01 §6.1) a partir de su código; el backend solo manda el código. */
const POINT_TITLES: Readonly<Record<string, string>> = {
  FISCAL_YEAR_IN_PREPARATION: 'Vigencia en preparación',
  VALUATION_RESOLUTION: 'Resolución de avalúos registrada con su PDF',
  VALUATION_CATALOG_PUBLISHED: 'Catálogo de avalúos publicado para la vigencia',
  TAX_RATES: 'Tarifas por rango registradas y sin solapamientos',
  TAX_CALENDAR: 'Calendario tributario registrado',
  TAX_BENEFITS: 'Beneficios configurados',
  PENALTY_RULES: 'Reglas de sanción registradas',
  SAMPLE_ASSESSMENT: 'Prueba de liquidación de muestra sin errores',
  JURISDICTION_CONFIG_PUBLISHED: 'Configuración de jurisdicción publicada',
};

const REASON_PLANNED =
  'La vigencia está Planeada: abre su preparación para empezar a cargar sus tablas.';
const REASON_IN_PREPARATION = 'La vigencia ya está en preparación: sus tablas se pueden cargar.';
const REASON_NOT_AVAILABLE = 'Opción aún no disponible';

const PAGE_DESCRIPTION =
  'Estado de la vigencia y lista de preparación para habilitar la liquidación.';
const LIST_BREADCRUMB = ['Administración', '1.4 Vigencias fiscales'];
const LIST_HREF = `/${OPTION_ROUTE}/1.4`;

/** Cuántos marcadores dibuja cada panel mientras carga (los del diseño, 68:4474). */
const DATA_SKELETONS = [1, 2];
const CHECKLIST_SKELETONS = [1, 2, 3, 4, 5];

type View = 'loading' | 'ready' | 'notFound' | 'error';

/** Cómo terminó una lectura: aplicada, fallida o descartada por una más nueva. */
type ReadResult = 'applied' | 'failed' | 'stale';

/** Un punto de la lista, listo para mostrar. */
interface Point {
  readonly number: string | undefined;
  readonly heading: string;
  readonly status: ChecklistItemStatus;
  readonly reason: string | undefined;
  readonly optional: boolean;
}

/** Una alerta de rechazo; el `id` cambia en cada intento para que se anuncie aunque el texto se repita. */
interface RejectionAlert {
  readonly id: number;
  readonly heading: string;
  readonly message: string;
}

/**
 * Detalle de una vigencia, opción 1.4 (007-02, 007-10 a 007-12): sus datos, su lista de preparación y, con
 * la acción `OPEN_PREPARATION`, «Abrir preparación». Todo lo que decide qué se ofrece y qué falta viene del
 * backend (`availableActions`, `preparation.missing`); la pantalla no calcula estados ni reglas, y el año
 * que no es de la tabla nacional ni siquiera se consulta. Diseño: Figma LIV-Diseño, `03 Pantallas`, sección
 * 1.4 (nodo 66:1966): En preparación 68:3460, Planeada 68:3846, no encontrada 68:4235 y cargando 68:4474.
 */
@Component({
  selector: 'app-fiscal-year-detail',
  imports: [
    PageHeaderComponent,
    ButtonComponent,
    PanelComponent,
    DescriptionItemComponent,
    ChecklistItemComponent,
    ResultStateComponent,
    SkeletonComponent,
    AlertComponent,
  ],
  templateUrl: './fiscal-year-detail.html',
  styleUrl: './fiscal-year-detail.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiscalYearDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fiscalYears = inject(FiscalYearsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly skeletons = { data: DATA_SKELETONS, checklist: CHECKLIST_SKELETONS };
  protected readonly pageDescription = PAGE_DESCRIPTION;

  protected readonly view = signal<View>('loading');
  /** El año de la ruta, si es uno que se puede consultar. */
  private readonly year = signal<number | undefined>(undefined);
  private readonly detail = signal<FiscalYearDetail | undefined>(undefined);
  /** Número de la última lectura pedida: la respuesta de una anterior no pisa la de una más nueva. */
  private latestRead = 0;
  private attempts = 0;
  /**
   * Cuenta los cambios de año: una operación iniciada para un año no puede escribir en la pantalla de otro.
   * `destroyed` evita escribir o pedir un dibujo cuando la persona ya salió (p. ej. tras un 403).
   */
  private generation = 0;
  private destroyed = false;

  /** Se está abriendo la preparación: el botón queda ocupado. */
  protected readonly opening = signal(false);
  /** La última relectura falló: lo que se ve puede estar viejo. */
  protected readonly refreshFailed = signal(false);
  protected readonly alerts = signal<RejectionAlert[]>([]);
  /** Lo que se anuncia a los lectores de pantalla; la región existe desde el inicio. */
  protected readonly notice = signal('');

  protected readonly heading = computed(() => {
    if (this.view() === 'notFound') {
      return 'Vigencia no encontrada';
    }
    const year = this.year();
    return year === undefined || this.view() === 'loading' ? 'Vigencia' : `Vigencia ${year}`;
  });
  protected readonly breadcrumb = computed(() => {
    const year = this.year();
    return this.view() === 'ready' || (this.view() === 'error' && year !== undefined)
      ? [...LIST_BREADCRUMB, String(year)]
      : LIST_BREADCRUMB;
  });
  protected readonly showDescription = computed(() => this.view() !== 'notFound');

  protected readonly summary = computed(() => {
    const detail = this.detail();
    if (!detail) {
      return undefined;
    }
    const known = Object.hasOwn(STATUSES, detail.status) ? STATUSES[detail.status] : undefined;
    return {
      year: String(detail.year),
      startsOn: formatBusinessDate(detail.startsOn, 'long') ?? 'Sin fecha',
      endsOn: formatBusinessDate(detail.endsOn, 'long') ?? 'Sin fecha',
      status: known?.label ?? detail.status,
      tone: known?.tone ?? 'neutral',
      liquidation: detail.assessmentsEnabled ? 'Habilitada' : 'No habilitada',
    };
  });

  /** «Abrir preparación» solo con la acción que manda el backend; las que no conoce se ignoran. */
  protected readonly canOpenPreparation = computed(
    () => this.detail()?.availableActions.includes(OPEN_PREPARATION) === true,
  );

  protected readonly points = computed<Point[]>(() =>
    (this.detail()?.preparation.items ?? []).map((item) => toPoint(item)),
  );
  protected readonly checklistIntro = computed(() =>
    describeRequirements(this.detail()?.preparation.items ?? []),
  );
  /** Por qué no se puede habilitar la liquidación: los puntos obligatorios que faltan, tal como los manda el backend. */
  protected readonly enableReason = computed(() =>
    describeMissing(this.detail()?.preparation.missing ?? []),
  );

  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => (this.destroyed = true));
    this.route.paramMap
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe((params) => this.show(parseYear(params.get('year'))));
  }

  /** Muestra la vigencia de un año nuevo: un año fuera de la tabla nacional es «no encontrada», sin llamar a la API. */
  private show(year: number | undefined): void {
    this.generation++;
    this.opening.set(false);
    this.year.set(year);
    this.detail.set(undefined);
    this.alerts.set([]);
    this.refreshFailed.set(false);
    this.notice.set('');
    void this.load();
  }

  /** Lee la vigencia y muestra «cargando» mientras tanto. */
  protected async load(): Promise<void> {
    const year = this.year();
    if (year === undefined) {
      // Una lectura que siga en curso de la vigencia anterior no debe pisar el «no encontrada».
      this.latestRead++;
      this.view.set('notFound');
      return;
    }
    this.view.set('loading');
    await this.read(year, false);
  }

  /**
   * Lee la vigencia. La respuesta de una lectura anterior a la última se descarta (`stale`). Una lectura
   * silenciosa conserva lo que hay a la vista si falla.
   */
  private async read(year: number, silent: boolean): Promise<ReadResult> {
    const read = ++this.latestRead;
    try {
      const detail = await this.fiscalYears.get(year);
      if (read !== this.latestRead) {
        return 'stale';
      }
      if (!isUsable(detail, year)) {
        // Una respuesta sin lo que la pantalla necesita (o de otro año) es un error de carga, no una pantalla rota.
        throw new Error('Respuesta de la vigencia incompleta');
      }
      this.detail.set(detail);
      this.view.set('ready');
      return 'applied';
    } catch (error) {
      if (read !== this.latestRead) {
        return 'stale';
      }
      if (!silent) {
        this.view.set(hasStatus(error, 404) ? 'notFound' : 'error');
      }
      return 'failed';
    }
  }

  /** Vuelve a leer la vigencia sin mostrar «cargando»; si falla, se conserva lo que hay y se avisa. */
  private async reload(year: number): Promise<ReadResult> {
    this.refreshFailed.set(false);
    const result = await this.read(year, true);
    if (result === 'failed') {
      this.refreshFailed.set(true);
    }
    return result;
  }

  /**
   * Abre la preparación de la vigencia, de Planeada a En preparación (007-10). Con un rechazo (V-06) o sin
   * saber el resultado, se vuelve a leer la vigencia. Un 403 lo atiende el interceptor.
   */
  protected async openPreparation(): Promise<void> {
    const year = this.year();
    if (year === undefined || this.opening()) {
      return;
    }
    const generation = this.generation;
    this.opening.set(true);
    this.alerts.set([]);
    this.notice.set('');
    try {
      await this.fiscalYears.openPreparation(year);
      if (!this.isCurrent(generation)) {
        return;
      }
      const result = await this.reload(year);
      if (this.isCurrent(generation)) {
        this.notice.set(
          result === 'failed'
            ? 'Se envió la apertura de la preparación, pero no pudimos actualizar la vigencia.'
            : `Se abrió la preparación de la vigencia ${year}.`,
        );
      }
    } catch (error) {
      if (!this.isCurrent(generation)) {
        return;
      }
      const rejection = openPreparationRejection(error, year);
      if (rejection) {
        this.alerts.set([
          { id: ++this.attempts, heading: rejection.heading, message: rejection.message },
        ]);
        if (rejection.refresh) {
          await this.reload(year);
        }
      }
    } finally {
      if (this.isCurrent(generation)) {
        this.opening.set(false);
        this.focusTitleIfLost();
      }
    }
  }

  /** La pantalla sigue mostrando la vigencia para la que se inició una operación (no cambió el año ni se salió). */
  private isCurrent(generation: number): boolean {
    return !this.destroyed && generation === this.generation;
  }

  protected backToList(): void {
    void this.router.navigateByUrl(LIST_HREF);
  }

  /**
   * Si la acción que tenía el foco ya no existe (la preparación se abrió o alguien más la abrió), el foco se
   * perdería en el documento: pasa al título de la pantalla. Si la persona lo llevó a otra parte (el menú, la
   * cabecera), no se le quita.
   */
  private focusTitleIfLost(): void {
    afterNextRender(
      () => {
        const root = this.host.nativeElement;
        const active = root.ownerDocument.activeElement;
        if (!active || active === root.ownerDocument.body || !active.isConnected) {
          const title = root.querySelector<HTMLElement>('h1');
          title?.setAttribute('tabindex', '-1');
          title?.focus();
        }
      },
      { injector: this.injector },
    );
  }
}

/** La respuesta trae lo que la pantalla usa y es de la vigencia pedida. */
function isUsable(detail: FiscalYearDetail, year: number): boolean {
  return (
    detail.year === year &&
    Array.isArray(detail.availableActions) &&
    Array.isArray(detail.preparation?.items) &&
    Array.isArray(detail.preparation.missing)
  );
}

/** El año de la ruta: un entero escrito sin ceros ni signos, de la tabla nacional; si no, `undefined`. */
function parseYear(param: string | null): number | undefined {
  if (param === null || !/^[0-9]+$/.test(param)) {
    return undefined;
  }
  const year = Number(param);
  return String(year) === param && year >= MIN_YEAR && year <= MAX_YEAR ? year : undefined;
}

function toPoint(item: PreparationItem): Point {
  const integer = Number.isInteger(item.number);
  const title = Object.hasOwn(POINT_TITLES, item.code) ? POINT_TITLES[item.code] : undefined;
  return {
    number: integer ? `${item.number}.` : undefined,
    heading: title ?? (integer ? `Punto ${item.number}` : 'Punto'),
    status: item.status === 'MET' ? 'met' : item.status === 'OBSERVED' ? 'observed' : 'pending',
    reason: describeReason(item),
    // Solo un `required` en falso es opcional: si falta el dato, el punto se trata como obligatorio.
    optional: item.required === false,
  };
}

/** Por qué el punto tiene su estado; un motivo que no conoce no se muestra. */
function describeReason(item: PreparationItem): string | undefined {
  if (item.reason === 'FISCAL_YEAR_PLANNED') {
    return REASON_PLANNED;
  }
  if (item.reason === 'OPTION_NOT_AVAILABLE') {
    return REASON_NOT_AVAILABLE;
  }
  return item.reason === 'MET' && item.code === 'FISCAL_YEAR_IN_PREPARATION'
    ? REASON_IN_PREPARATION
    : undefined;
}

/** Los números enteros distintos, de menor a mayor. */
function sortedNumbers(numbers: readonly number[]): number[] {
  return [...new Set(numbers.filter((value) => Number.isInteger(value)))].sort((a, b) => a - b);
}

/** «A, B y C»; con uno solo, ese; sin ninguno, vacío. */
function joinList(parts: readonly string[]): string {
  return parts.length > 1 ? `${parts.slice(0, -1).join(', ')} y ${parts.at(-1)}` : (parts[0] ?? '');
}

/** Agrupa números consecutivos: tres o más seguidos son «1 a 5» (1, 2, 3, 4, 5). */
function describeRanges(numbers: readonly number[]): string {
  const parts: string[] = [];
  for (let start = 0; start < numbers.length;) {
    let end = start;
    while (numbers[end + 1] === numbers[end] + 1) {
      end++;
    }
    if (end - start >= 2) {
      parts.push(`${numbers[start]} a ${numbers[end]}`);
    } else {
      parts.push(...numbers.slice(start, end + 1).map(String));
    }
    start = end + 1;
  }
  return joinList(parts);
}

/** La frase de obligatorios y opcionales, armada con `required` de cada punto. */
function describeRequirements(items: readonly PreparationItem[]): string {
  const required = sortedNumbers(
    items.filter((item) => item.required !== false).map((item) => item.number),
  );
  const optional = sortedNumbers(
    items.filter((item) => item.required === false).map((item) => item.number),
  );
  const sentences: string[] = [];
  if (required.length === 0) {
    sentences.push('Ningún punto es obligatorio');
  } else if (required.length === 1) {
    sentences.push(`El punto ${required[0]} es obligatorio`);
  } else {
    sentences.push(`Los puntos ${describeRanges(required)} son obligatorios`);
  }
  if (optional.length > 0) {
    const names = joinList(optional.map((number) => `el ${number}`));
    sentences.push(`${names} ${optional.length === 1 ? 'es opcional' : 'son opcionales'}`);
  }
  return `${sentences.join('; ')}. Cada punto lo verifica el sistema.`;
}

/** Por qué «Habilitar liquidación» está inhabilitado: los puntos obligatorios que faltan (V-03, CA-14). */
function describeMissing(missing: readonly number[]): string {
  const numbers = sortedNumbers(missing);
  if (numbers.length === 0) {
    return 'Aún no se puede habilitar la liquidación.';
  }
  const list = joinList(numbers.map(String));
  return numbers.length === 1
    ? `Falta 1 punto obligatorio: ${list}.`
    : `Faltan ${numbers.length} puntos obligatorios: ${list}.`;
}
