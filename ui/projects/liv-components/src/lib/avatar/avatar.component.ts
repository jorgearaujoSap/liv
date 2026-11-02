import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Tamaño del avatar: 24 px (`sm`), 32 px (`md`) o 40 px (`lg`). */
export type AvatarSize = 'sm' | 'md' | 'lg';

/**
 * Iniciales del usuario (Figma: `Avatar`). Es decorativo: el nombre completo siempre aparece junto a él,
 * así que los lectores de pantalla lo omiten.
 */
@Component({
  selector: 'liv-avatar',
  template: '{{ initials() }}',
  styleUrl: './avatar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[attr.data-size]': 'size()',
  },
})
export class AvatarComponent {
  readonly initials = input.required<string>();
  readonly size = input<AvatarSize>('sm');
}
