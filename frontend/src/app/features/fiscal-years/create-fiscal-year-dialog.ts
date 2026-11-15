import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
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
import { creationRejection } from './fiscal-year-rejections';
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

/**
 * Diálogo de creación de una vigencia (007-04 a 007-08). Las opciones son los años que el backend dice que se
 * pueden crear (`years`, de `creatableYears`), con sus fechas y su etiqueta; la pantalla no calcula ninguno.
 * Mientras abre hay siempre un año marcado. Los rechazos de la API se muestran en una alerta con el código de
 * la regla; ante un V-01 (otra persona creó el año antes) avisa con `conflict` para que la lista se vuelva a
 * leer, y las `years` nuevas dejan marcado el primer año que siga disponible. Diseño: Figma LIV-Diseño, 67:3040
 * (el diálogo), 67:3163 (con un rechazo), 67:3284 (los mensajes) y 75:4479 (la etiqueta de un año pasado).
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

  /** Los años que se pueden crear, como los manda el backend. */
  readonly years = input.required<readonly CreatableYear[]>();
  /** La vigencia quedó creada. */
  readonly created = output<FiscalYear>();
  /** Otra persona creó el año antes (V-01): la lista que se muestra ya no es la vigente. */
  readonly conflict = output<void>();
  /** La persona canceló o cerró el diálogo con Escape. */
  readonly closed = output<void>();

  /** El año marcado, como texto (el valor de un radio): conserva el actual si sigue disponible y, si no, el primero. */
  protected readonly selected = linkedSignal<readonly CreatableYear[], string | null>({
    source: this.years,
    computation: (years, previous) => pickYear(years, previous?.value),
  });
  protected readonly choice = computed(() =>
    this.years().find((candidate) => String(candidate.year) === this.selected()),
  );
  protected readonly startsOn = computed(() => formatBusinessDate(this.choice()?.startsOn) ?? '');
  protected readonly endsOn = computed(() => formatBusinessDate(this.choice()?.endsOn) ?? '');

  protected readonly submitting = signal(false);
  protected readonly alerts = signal<readonly RejectionAlert[]>([]);
  /** Cuenta los intentos solo para dar a cada alerta un `id` nuevo; no se muestra, así que no es una señal. */
  private attempts = 0;

  protected relationLabel(relation: string): string {
    return RELATION_LABELS[relation] ?? '';
  }

  protected async submit(): Promise<void> {
    const choice = this.choice();
    if (!choice || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.alerts.set([]);
    try {
      this.created.emit(await this.fiscalYears.create(choice.year));
    } catch (error) {
      this.reject(error, choice.year);
    } finally {
      this.submitting.set(false);
    }
  }

  /** Escape y «Cancelar» cierran, pero no mientras se envía: la creación sigue y su resultado importa. */
  protected close(): void {
    if (!this.submitting()) {
      this.closed.emit();
    }
  }

  private reject(error: unknown, year: number): void {
    const rejection = creationRejection(error, year);
    if (!rejection) {
      return;
    }
    this.alerts.set([
      { id: ++this.attempts, heading: rejection.heading, message: rejection.message },
    ]);
    if (rejection.conflict) {
      this.conflict.emit();
    }
  }
}

/** El año que queda marcado: el que ya estaba, si sigue disponible, y si no el primero (o ninguno sin años). */
function pickYear(
  years: readonly CreatableYear[],
  previous: string | null | undefined,
): string | null {
  if (years.some((candidate) => String(candidate.year) === previous)) {
    return previous ?? null;
  }
  return years.length > 0 ? String(years[0].year) : null;
}
