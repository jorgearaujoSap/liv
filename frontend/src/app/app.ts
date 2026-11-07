import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Raíz de la aplicación: solo aloja las rutas. El layout, el inicio de sesión y el menú se construyen
 * con liv-components cuando sus diseños estén aprobados en Figma (entregables F0‑05 y F0‑05b).
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
