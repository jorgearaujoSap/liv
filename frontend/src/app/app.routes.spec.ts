import { authGuard } from './core/session/auth.guard';
import { capabilityGuard } from './core/capabilities/capability.guard';
import { LoadError } from './features/errors/load-error';
import { Shell } from './features/shell/shell';
import { routes } from './app.routes';
import { APP_PATHS } from './core/app-paths';

const shell = () => routes.find((route) => route.component === Shell);
const child = (path: string) => shell()?.children?.find((route) => route.path === path);

// Las rutas de las pantallas de la feature 001 (T22): las guardas ya existen desde T21.
describe('rutas', () => {
  it('toda la aplicación, salvo el error, vive dentro de la estructura general y exige sesión', () => {
    expect(shell()?.path).toBe('');
    expect(shell()?.canActivate).toEqual([authGuard]);
  });

  it('«Error» no exige sesión: sale precisamente cuando no se pudo saber si la hay', () => {
    const error = routes.find((route) => route.component === LoadError);

    expect(error?.path).toBe(APP_PATHS.error.slice(1));
    expect(error?.canActivate).toBeUndefined();
  });

  it('Sin permiso y Próximamente están dentro de la estructura, con las rutas que fijó T21', () => {
    expect(child(APP_PATHS.forbidden.slice(1))).toBeDefined();
    expect(child(APP_PATHS.comingSoon.slice(1))).toBeDefined();
  });

  it('Inicio es la ruta raíz', () => {
    expect(shell()?.children?.[0]?.path).toBe('');
    expect(shell()?.children?.[0]?.pathMatch).toBe('full');
  });

  it('cualquier opción del menú se abre por URL por una ruta con la guarda de capacidades (001-16)', () => {
    const option = child('opcion/:code');

    expect(option?.canActivate).toEqual([capabilityGuard]);
    expect(option?.data).toEqual({ capabilityParam: 'code' });
  });

  it('una dirección desconocida vuelve a Inicio, sin inventar una pantalla', () => {
    const fallback = routes.find((route) => route.path === '**');

    expect(fallback?.redirectTo).toBe('');
  });
});
