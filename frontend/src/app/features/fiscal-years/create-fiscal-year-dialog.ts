import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  linkedSignal,
  output,
  signal,
  untracked,
} from '@angular/core';
import {
  AlertComponent,
  ButtonComponent,
  DescriptionItemComponent,
  DialogComponent,
  formatBusinessDate,
  SelectComponent,
  type SelectOption,
} from 'liv-components';

import type { CreatableYear, FiscalYear } from '../../api/tax/models';
import { creationRejection, isOutcomeUnknown, type Rejection } from './fiscal-year-rejections';
import { FiscalYearsService } from './fiscal-years.service';

/**
 * Qué dice la etiqueta de un año según su relación con el año en curso (E4-4). Un año pasado, o con una relación
 * que no conoce, solo muestra su número.
 */
const RELATION_LABELS: Readonly<Record<string, string>> = {
  CURRENT: 'Año en curso',
  NEXT: 'Año siguiente al año en curso',
};

/**
 * Qué pasa con el año elegido cuando llegan los años nuevos (ver `selected`): `none`, sin un rechazo antes;
 * `keepAttempted`, tras un resultado que no se conoce; `startOver`, tras un V-01.
 */
type YearsAfterRejection = 'none' | 'keepAttempted' | 'startOver';

/** Una alerta de rechazo. El `id` cambia en cada intento para que el lector de pantalla la anuncie de nuevo. */
interface RejectionAlert {
  readonly id: number;
  readonly heading: string;
  readonly message: string;
}

/** Lo que el diálogo avisa cuando la lista que se muestra pudo quedar vieja (V-01, o no se sabe si se creó). */
export interface StaleList {
  /** El año que se intentó crear. */
  readonly year: number;
  /** No se sabe si la vigencia se creó (tiempo agotado, caída o error del servidor); si no, fue un V-01. */
  readonly unknown: boolean;
  /** El mensaje que se mostró, por si la pantalla debe conservarlo al cerrar el diálogo. */
  readonly rejection: Rejection;
}

/**
 * Diálogo de creación de una vigencia (007-04 a 007-08, 007-22). El año se elige en una lista desplegable
 * (`Select`) cuyas opciones son los años que el backend dice que se pueden crear (`years`, de `creatableYears`),
 * en su orden y con su etiqueta; la pantalla no calcula ninguno. Viene elegido el año en curso o, si no se puede
 * crear, ninguno: nunca un año pasado. El foco inicial va a la lista y no a «Crear vigencia»: crear no se puede
 * deshacer y no debe bastar con pulsar Enter al abrir. Con un año pasado elegido, una advertencia propia
 * reemplaza a la general. Los rechazos de la API se muestran en una alerta con el código de la regla; ante un
 * V-01 (otra persona creó el año antes) o un resultado que no se conoce avisa con `staleList` para que la lista
 * se vuelva a leer. La lista queda inhabilitada mientras se envía y mientras se relee, así que el año intentado
 * es siempre el que se envió (H2-18). Diseño: Figma LIV-Diseño, 67:3040 (el diálogo), 82:5802 (un año pasado),
 * 67:3163 (con un rechazo) y 67:3284 (los mensajes).
 */
@Component({
  selector: 'app-create-fiscal-year-dialog',
  imports: [
    DialogComponent,
    ButtonComponent,
    SelectComponent,
    DescriptionItemComponent,
    AlertComponent,
  ],
  templateUrl: './create-fiscal-year-dialog.html',
  styleUrl: './create-fiscal-year-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateFiscalYearDialog {
  private readonly fiscalYears = inject(FiscalYearsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Los años que se pueden crear, como los manda el backend. */
  readonly years = input.required<readonly CreatableYear[]>();
  /** La pantalla está releyendo la lista: la creación espera para no ofrecer años que ya no están. */
  readonly refreshing = input(false);
  /** La última relectura de la lista falló: los años que se ofrecen pueden no estar al día. */
  readonly refreshFailed = input(false);
  /** La vigencia quedó creada. */
  readonly created = output<FiscalYear>();
  /** La lista que se muestra pudo quedar vieja: V-01, o no se sabe si la vigencia se creó. */
  readonly staleList = output<StaleList>();
  /** La persona canceló o cerró el diálogo con Escape. */
  readonly closed = output<void>();

  /**
   * El año elegido, como texto (el valor de la lista), o `null` si no hay ninguno. Cuando llegan años nuevos:
   * - al abrir y después de un V-01 (`startOver`), el año en curso si viene en la lista y, si no, ninguno (E4-3);
   * - después de un resultado que no se conoce (`keepAttempted`), el año intentado si todavía está y, si no,
   *   ninguno (H2-18): el diálogo no cambia por su cuenta lo que la persona eligió;
   * - en cualquier otro cambio de la lista (`none`), el año elegido si sigue y, si no, lo mismo que al abrir.
   */
  protected readonly selected = linkedSignal<readonly CreatableYear[], string | null>({
    source: this.years,
    computation: (years, previous) => {
      const mode = this.afterRejection;
      this.afterRejection = 'none';
      return pickYear(years, previous?.value ?? null, mode);
    },
  });
  protected readonly choice = computed(() =>
    this.years().find((candidate) => String(candidate.year) === this.selected()),
  );
  protected readonly options = computed<SelectOption[]>(() =>
    this.years().map((candidate) => ({
      value: String(candidate.year),
      label: describeYear(candidate),
    })),
  );
  /** El año elegido es uno pasado: lo dice la relación que manda el servidor, no un cálculo de fechas. */
  protected readonly isPast = computed(() => this.choice()?.relation === 'PAST');
  protected readonly startsOn = computed(
    () => formatBusinessDate(this.choice()?.startsOn, 'long') ?? '',
  );
  protected readonly endsOn = computed(
    () => formatBusinessDate(this.choice()?.endsOn, 'long') ?? '',
  );

  protected readonly submitting = signal(false);
  protected readonly busyLabel = computed(() =>
    this.submitting() ? 'Creando la vigencia…' : 'Actualizando la lista…',
  );
  protected readonly alerts = signal<readonly RejectionAlert[]>([]);
  /** Cuenta los intentos solo para dar a cada alerta un `id` nuevo; no se muestra, así que no es una señal. */
  private attempts = 0;
  /** Cerrojo del envío: tras crear queda puesto (el diálogo se va a cerrar) y solo un rechazo lo libera. */
  private locked = false;
  /** Qué pasa con el año elegido en la próxima lista de años: lo fija un rechazo y `selected` lo consume. */
  private afterRejection: YearsAfterRejection = 'none';
  /** Tras un rechazo que pide releer la lista, el foco vuelve a la lista cuando termina la relectura. */
  private focusListWhenReloaded = false;

  constructor() {
    effect(() => {
      this.years();
      if (!this.refreshing()) {
        untracked(() => this.focusListIfPending());
      }
    });
  }

  protected async submit(): Promise<void> {
    const choice = this.choice();
    if (!choice || this.locked || this.refreshing()) {
      return;
    }
    this.locked = true;
    this.afterRejection = 'none';
    this.focusListWhenReloaded = false;
    this.submitting.set(true);
    this.alerts.set([]);
    let created: FiscalYear;
    try {
      created = await this.fiscalYears.create(choice.year);
    } catch (error) {
      this.locked = false;
      this.reject(error, choice.year);
      return;
    } finally {
      this.submitting.set(false);
    }
    // Fuera del `try`: un fallo de quien escucha no es un rechazo de la creación.
    this.created.emit(created);
  }

  /** Escape y «Cancelar» cierran, pero no mientras se envía: la creación sigue y su resultado importa. */
  protected close(): void {
    if (!this.submitting()) {
      this.closed.emit();
    }
  }

  /**
   * Cuando termina la relectura, el foco vuelve a la lista: con ella inhabilitada lo perdió, y no debe quedar en
   * «Crear vigencia», donde un Enter crearía otro año sin avisar. También si la relectura falló o si no queda
   * ningún año elegido.
   */
  private focusListIfPending(): void {
    if (!this.focusListWhenReloaded) {
      return;
    }
    this.focusListWhenReloaded = false;
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>('liv-select select')?.focus(),
      { injector: this.injector },
    );
  }

  private reject(error: unknown, year: number): void {
    const rejection = creationRejection(error, year);
    if (!rejection) {
      return;
    }
    this.alerts.set([
      { id: ++this.attempts, heading: rejection.heading, message: rejection.message },
    ]);
    if (rejection.reload) {
      const unknown = isOutcomeUnknown(error);
      this.afterRejection = unknown ? 'keepAttempted' : 'startOver';
      this.focusListWhenReloaded = true;
      this.staleList.emit({
        year,
        unknown,
        rejection: { heading: rejection.heading, message: rejection.message },
      });
    }
  }
}

/** Lo que dice la lista de un año: su número y, si es el en curso o el siguiente, su etiqueta (E4-4). */
function describeYear(candidate: CreatableYear): string {
  return Object.hasOwn(RELATION_LABELS, candidate.relation)
    ? `${candidate.year} · ${RELATION_LABELS[candidate.relation]}`
    : String(candidate.year);
}

/** El año que queda elegido cuando llegan `years`, según lo que había y lo que pasó antes (ver `selected`). */
function pickYear(
  years: readonly CreatableYear[],
  kept: string | null,
  mode: YearsAfterRejection,
): string | null {
  const stillThere = years.some((candidate) => String(candidate.year) === kept);
  if (mode === 'keepAttempted') {
    return stillThere ? kept : null;
  }
  if (mode === 'none' && stillThere) {
    return kept;
  }
  return currentYear(years);
}

/** El año en curso si se puede crear y, si no, ninguno: nunca el siguiente ni uno pasado. */
function currentYear(years: readonly CreatableYear[]): string | null {
  const current = years.find((candidate) => candidate.relation === 'CURRENT');
  return current ? String(current.year) : null;
}
