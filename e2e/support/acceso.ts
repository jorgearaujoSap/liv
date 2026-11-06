import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

import { CLAVE, KEYCLOAK_URL, TOTP_SECRETO } from './entorno';
import { codigoTotp } from './totp';

/** Espera la página de acceso de Keycloak (tema `liv`). */
export async function enPaginaDeAcceso(page: Page): Promise<void> {
  await expect(page).toHaveURL(new RegExp(`^${KEYCLOAK_URL}/realms/liv/`));
  await expect(page.locator('#username')).toBeVisible();
}

/** Escribe usuario y contraseña en la página de acceso y la envía. */
export async function ingresar(page: Page, usuario: string, clave = CLAVE): Promise<void> {
  await enPaginaDeAcceso(page);
  await page.locator('#username').fill(usuario);
  await page.locator('#password').fill(clave);
  await page.locator('#kc-login').click();
}

/** Intervalo de 30 s del último código que se usó: Keycloak no acepta el mismo código dos veces. */
let ultimoIntervalo = -1;

/**
 * Escribe el código del segundo factor. Espera al intervalo siguiente si el actual está por vencer (para que no
 * cambie entre que se calcula y que Keycloak lo valida) o si su código ya se usó.
 */
export async function escribirCodigo(page: Page, codigo?: string): Promise<void> {
  await expect(page.locator('#otp')).toBeVisible();
  if (codigo === undefined) {
    const intervalo = () => Math.floor(Date.now() / 30_000);
    const restante = 30_000 - (Date.now() % 30_000);
    if (restante < 3_000 || intervalo() === ultimoIntervalo) {
      await page.waitForTimeout(restante + 200);
    }
    ultimoIntervalo = intervalo();
    codigo = codigoTotp(TOTP_SECRETO);
  }
  await page.locator('#otp').fill(codigo);
  await page.locator('#kc-login').click();
}

/** Entra a LIV desde cero con un usuario sin segundo factor y espera a Inicio. */
export async function entrarComo(page: Page, usuario: string): Promise<void> {
  await page.goto('/');
  await ingresar(page, usuario);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola,');
}

/** Infracciones de axe (WCAG 2.1 AA) en la página actual; vacío si cumple. */
export async function infraccionesDeAccesibilidad(page: Page): Promise<string[]> {
  const resultado = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  return resultado.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.length})`);
}
