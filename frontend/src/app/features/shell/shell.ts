import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  OnInit,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import {
  AppHeaderComponent,
  LogoComponent,
  NavItemComponent,
  NavSectionComponent,
  SideNavComponent,
  SkeletonComponent,
} from 'liv-components';
import { filter } from 'rxjs';

import { APP_PATHS, OPTION_ROUTE } from '../../core/app-paths';
import { hasStatus } from '../../core/http/http-errors';
import { optionPath } from '../../core/navigation/menu-presentation';
import { IdleService } from '../../core/session/idle.service';
import { SessionService } from '../../core/session/session.service';
import { roleLabels } from './role-labels';
import { SessionWarningDialog } from './session-warning-dialog';
import { WorkspaceService } from './workspace.service';

/**
 * Estructura general de la aplicación (001-02, 001-15): menú lateral, cabecera con la persona y su
 * administración, y el área donde entra cada pantalla. Mientras carga quién es la persona y qué ve, muestra
 * el marco de «Cargando»; si no carga, lleva a «Error». Diseño: Figma LIV-Diseño, `03 Pantallas`, «Inicio»
 * (nodo 26:793) y «Cargando» (nodo 26:1640).
 *
 * El menú es lo que el backend permite (`WorkspaceService.menu`): la estructura solo decide cómo se ve.
 * Se pintan tres niveles, como en Figma («Menú · tres niveles», nodo 51:1519): el módulo, sus opciones y,
 * en las que agrupan otras (1.5 y 1.6), las suyas. Un nivel más profundo no se pinta: el catálogo de Doc. 01
 * §3 no lo tiene.
 *
 * Qué ramas están abiertas es estado de la estructura: al navegar se abren las que contienen la pantalla
 * actual, y la persona puede abrir y contraer cualquiera; ninguna se cierra sola, para no perder el foco
 * (`NavItem`, `[(expanded)]`).
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    SideNavComponent,
    NavItemComponent,
    NavSectionComponent,
    AppHeaderComponent,
    LogoComponent,
    SkeletonComponent,
    SessionWarningDialog,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell implements OnInit {
  private readonly router = inject(Router);
  private readonly workspace = inject(WorkspaceService);
  private readonly idle = inject(IdleService);
  private readonly session = inject(SessionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly ready = signal(false);
  protected readonly user = this.workspace.user;
  protected readonly menu = this.workspace.menu;
  protected readonly roles = computed(() => roleLabels(this.workspace.user()?.roles ?? []));

  /** Anchos de los marcadores del menú lateral y de las tarjetas del marco de carga (diseño de «Cargando»). */
  protected readonly menuPlaceholders = [200, 150, 180, 120, 170, 140, 190, 130];
  protected readonly cardPlaceholders = Array.from({ length: 9 }, (_, index) => index);

  private readonly url = signal(this.router.url);
  private readonly path = computed(() => this.url().split(/[?#]/)[0]);
  protected readonly atHome = computed(() => this.path() === '/');
  /** Códigos de las ramas abiertas (módulos y opciones que agrupan otras). */
  private readonly opened = signal<ReadonlySet<string>>(new Set());

  constructor() {
    // Al navegar, se abren las ramas que contienen la pantalla actual: `/opcion/1.5.3` abre `1` y `1.5`.
    effect(() => {
      const branches = branchesOf(this.path());
      if (branches.length > 0) {
        untracked(() => this.opened.update((opened) => new Set([...opened, ...branches])));
      }
    });
  }

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.url.set(this.router.url));

    let destroyed = false;
    this.destroyRef.onDestroy(() => (destroyed = true));
    this.workspace.load().then(
      () => this.ready.set(true),
      (error: unknown) => {
        // Sin sesión (401) el interceptor lleva por completo a iniciar sesión, y «Error» no debe pisarlo; pero
        // si no pudo averiguar si hay sesión no redirige, y sin «Error» la persona se quedaría en «Cargando».
        // Y si la estructura ya se destruyó, hay otra navegación en curso que tampoco se pisa.
        const toLogin = hasStatus(error, 401) && this.session.isRedirecting;
        if (!destroyed && !toLogin) {
          void this.router.navigateByUrl(APP_PATHS.error);
        }
      },
    );
  }

  /** La pantalla actual es esta opción. */
  protected isCurrent(href: string | undefined): boolean {
    return href !== undefined && this.path() === href;
  }

  /**
   * La pantalla actual está dentro de esta rama: `/opcion/1.4` es del módulo `1`, y `/opcion/1.5.3` es del
   * módulo `1` y de su grupo `1.5`.
   */
  protected isInModule(code: string): boolean {
    const own = optionPath(code);
    return this.path() === own || this.path().startsWith(`${own}.`);
  }

  protected isExpanded(code: string): boolean {
    return this.opened().has(code);
  }

  protected setExpanded(code: string, expanded: boolean): void {
    this.opened.update((opened) => {
      const next = new Set(opened);
      if (expanded) {
        next.add(code);
      } else {
        next.delete(code);
      }
      return next;
    });
  }

  protected go(href: string): void {
    void this.router.navigateByUrl(href);
  }

  protected signOut(): void {
    void this.idle.logout();
  }
}

/** Forma de un número de opción: `1`, `1.5` o `1.5.9`. */
const OPTION_CODE = /^[0-9]+(\.[0-9]+)*$/;

/**
 * Las ramas que contienen una opción del menú: de `/opcion/1.5.3`, `1` y `1.5`. Una ruta que no es de una
 * opción (`/`, `/opcion/abc`, `/opcion/1..2`) no abre nada.
 */
function branchesOf(path: string): string[] {
  const prefix = `/${OPTION_ROUTE}/`;
  if (!path.startsWith(prefix)) {
    return [];
  }
  const code = path.slice(prefix.length);
  if (!OPTION_CODE.test(code)) {
    return [];
  }
  const parts = code.split('.');
  return parts.slice(0, -1).map((_, index) => parts.slice(0, index + 1).join('.'));
}
