import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/** Esqueleto del `Select` para la prueba en rojo; la implementación llega en el commit siguiente. */
export interface SelectOption {
  value: string;
  label: string;
}

@Component({
  selector: 'liv-select',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectComponent {
  readonly label = input.required<string>();
  readonly value = model<string | null>(null);
  readonly options = input<readonly SelectOption[]>([]);
  readonly placeholder = input<string>();
  readonly help = input<string>();
  readonly error = input<string>();
  readonly disabled = input(false);
}
