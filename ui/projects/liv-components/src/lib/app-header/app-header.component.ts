import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { AvatarComponent } from '../avatar/avatar.component';
import { ButtonComponent } from '../button/button.component';
import { IconComponent } from '../icon/icon.component';

const UNKNOWN_INITIALS = '?';

/**
 * Barra superior del área de trabajo (Figma: `AppHeader`, alto 64, a la derecha del `SideNav`). A la
 * izquierda, la administración activa; a la derecha, el avatar, el nombre y el rol del usuario y "Salir".
 * Todos los datos vienen del servidor (`GET /api/v1/me`), nunca de lo que envía el navegador. "Salir" solo
 * emite `signOut`: la aplicación cierra la sesión en LIV y en el proveedor de identidad.
 *
 * Las iniciales del avatar salen del nombre: la primera letra de la primera y de la última palabra.
 */
@Component({
  selector: 'liv-app-header',
  imports: [AvatarComponent, ButtonComponent, IconComponent],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppHeaderComponent {
  /** Administración activa (`Administration` de Figma), p. ej. "Municipio de Ipiales". */
  readonly administration = input.required<string>();
  /** Nombre completo del usuario (`User name` de Figma). */
  readonly userName = input.required<string>();
  /** Rol del usuario (`Role` de Figma); sin él, solo se muestra el nombre. */
  readonly role = input<string>();
  /** El usuario pidió cerrar la sesión. */
  readonly signOut = output<void>();

  protected readonly roleText = computed(() => this.role()?.trim() || null);
  protected readonly initials = computed(() => initialsOf(this.userName()));
}

/** Primera letra de la primera y de la última palabra, en mayúscula; `?` si el nombre está vacío. */
function initialsOf(name: string): string {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return UNKNOWN_INITIALS;
  }
  const last = words.length > 1 ? firstLetter(words[words.length - 1]) : '';
  return (firstLetter(words[0]) + last).toUpperCase();
}

/** Primer carácter de la palabra, sin partir un par sustituto (un emoji o un carácter fuera del plano básico). */
function firstLetter(word: string): string {
  return Array.from(word)[0];
}
