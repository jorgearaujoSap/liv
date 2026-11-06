import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
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

import { APP_PATHS } from '../../core/app-paths';
import { hasStatus } from '../../core/http/http-errors';
import { optionPath } from '../../core/navigation/menu-presentation';
import { IdleService } from '../../core/session/idle.service';
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
 * Se pintan dos niveles (módulo y sus opciones); el tercero (1.5.x y 1.6.x) no tiene diseño todavía.
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
        // Sin sesión (401) el interceptor ya navega por completo a iniciar sesión: «Error» no debe pisarlo. Y
        // si la estructura ya se destruyó, hay otra navegación en curso que tampoco se pisa.
        if (!destroyed && !hasStatus(error, 401)) {
          void this.router.navigateByUrl(APP_PATHS.error);
        }
      },
    );
  }

  /** La pantalla actual es esta opción. */
  protected isCurrent(href: string | undefined): boolean {
    return href !== undefined && this.path() === href;
  }

  /** La pantalla actual es una opción de este módulo (`/opcion/1.4` es del módulo `1`). */
  protected isInModule(code: string): boolean {
    const own = optionPath(code);
    return this.path() === own || this.path().startsWith(`${own}.`);
  }

  protected go(href: string): void {
    void this.router.navigateByUrl(href);
  }

  protected signOut(): void {
    void this.idle.logout();
  }
}
