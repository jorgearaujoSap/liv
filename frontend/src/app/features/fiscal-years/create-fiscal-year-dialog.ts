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
  RadioGroupComponent,
  RadioOptionComponent,
} from 'liv-components';

import type { CreatableYear, FiscalYear } from '../../api/tax/models';
import { creationRejection, isOutcomeUnknown, type Rejection } from './fiscal-year-rejections';
import { FiscalYearsService } from './fiscal-years.service';

/** Qué dice la etiqueta de un año según su relación con el año en curso; una relación que no conoce no lleva. */
const RELATION_LABELS: Readonly<Record<string, string>> = {
  CURRENT: 'Año en curso',
  NEXT: 'Año siguiente al año en curso',
  PAST: 'Año anterior al año en curso',
};

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
 * Diálogo de creación de una vigencia (007-04 a 007-08). Las opciones son los años que el backend dice que se
 * pueden crear (`years`, de `creatableYears`), con sus fechas y su etiqueta; la pantalla no calcula ninguno.
 * Al abrir queda marcado el año en curso o, si no viene, el siguiente (nunca uno pasado), y el foco inicial va a él
 * y no a «Crear vigencia»: crear no se puede deshacer y no debe bastar con pulsar Enter al abrir. Los rechazos de
 * la API se muestran en una alerta con el código de la regla; ante un V-01 (otra persona creó el año antes) o un
 * resultado que no se conoce avisa con `staleList` para que la lista se vuelva a leer, y las `years` nuevas
 * conservan el año marcado si sigue disponible y, si no, marcan el año en curso o el siguiente. Diseño: Figma
 * LIV-Diseño, 67:3040 (el diálogo), 67:3163 (con un rechazo), 67:3284 (los mensajes) y 75:4479 (la etiqueta de un
 * año pasado).
 */
@Component({
  selector: 'app-create-fiscal-year-dialog',
  imports: [
    DialogComponent,
    ButtonComponent,
    RadioGroupComponent,
    RadioOptionComponent,
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

  /** El año marcado, como texto (el valor de un radio): conserva el actual si sigue disponible y, si no, el año en curso o el siguiente. */
  protected readonly selected = linkedSignal<readonly CreatableYear[], string | null>({
    source: this.years,
    computation: (years, previous) => pickYear(years, previous?.value),
  });
  protected readonly choice = computed(() =>
    this.years().find((candidate) => String(candidate.year) === this.selected()),
  );
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
  /** Tras un rechazo que pide releer la lista, el foco va al año marcado cuando lleguen los años nuevos. */
  private refocusOnNextYears = false;

  constructor() {
    effect(() => {
      this.years();
      untracked(() => this.focusYearIfPending());
    });
  }

  protected relationLabel(relation: string): string {
    return Object.hasOwn(RELATION_LABELS, relation) ? RELATION_LABELS[relation] : '';
  }

  protected async submit(): Promise<void> {
    const choice = this.choice();
    if (!choice || this.locked || this.refreshing()) {
      return;
    }
    this.locked = true;
    this.refocusOnNextYears = false;
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
   * La lista nueva puede cambiar el año marcado (el que ya existía deja de estar): el foco va al año marcado
   * para que se lea cuál es, y no se queda en «Crear vigencia», donde un Enter crearía otro año sin avisar. Si la
   * lista nueva no deja ningún año marcado, el foco va al primer radio del grupo.
   */
  private focusYearIfPending(): void {
    if (!this.refocusOnNextYears) {
      return;
    }
    this.refocusOnNextYears = false;
    afterNextRender(
      () => {
        const radio = (selector: string) =>
          this.host.nativeElement.querySelector<HTMLElement>(selector);
        (radio('input[type="radio"]:checked') ?? radio('input[type="radio"]'))?.focus();
      },
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
      this.refocusOnNextYears = true;
      this.staleList.emit({
        year,
        unknown: isOutcomeUnknown(error),
        rejection: { heading: rejection.heading, message: rejection.message },
      });
    }
  }
}

/**
 * El año que queda marcado: el que ya estaba, si sigue disponible; si no, el año en curso; si no viene, el
 * siguiente; y si tampoco, ninguno. Nunca un año pasado por omisión, ni por su posición en la lista (riesgo 6 de
 * la spec: el contrato 1.1.0 manda del más reciente al más antiguo). Regla transitoria hasta el diálogo con `Select`.
 */
function pickYear(
  years: readonly CreatableYear[],
  previous: string | null | undefined,
): string | null {
  if (years.some((candidate) => String(candidate.year) === previous)) {
    return previous ?? null;
  }
  const preferred =
    years.find((candidate) => candidate.relation === 'CURRENT') ??
    years.find((candidate) => candidate.relation === 'NEXT');
  return preferred ? String(preferred.year) : null;
}
