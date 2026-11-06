import { readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

import { CLAVE, KEYCLOAK_URL, TOTP_SECRETO } from './entorno';
import { codigoTotp } from './totp';

/** Espera la página de acceso de Keycloak (tema `liv`). */
export async function enPaginaDeAcceso(page: Page): Promise<void> {
  await expect(page).toHaveURL(new RegExp(`^${KEYCLOAK_URL}/realms/liv/`));
  await expect(page.locator('#username')).toBeVisible();
}

/**
 * Envía un formulario de Keycloak y espera su respuesta. Así lo que se compruebe después es la página nueva, no el
 * mensaje que quedó del intento anterior.
 */
export async function enviar(page: Page, boton = '#kc-login'): Promise<void> {
  await Promise.all([
    page.waitForResponse(
      (r) => r.request().method() === 'POST' && r.url().startsWith(`${KEYCLOAK_URL}/realms/liv/login-actions/`),
    ),
    page.locator(boton).click(),
  ]);
  await page.waitForLoadState();
}

/** Escribe usuario y contraseña en la página de acceso, la envía y espera la respuesta de Keycloak. */
export async function ingresar(page: Page, usuario: string, clave = CLAVE): Promise<void> {
  await enPaginaDeAcceso(page);
  await page.locator('#username').fill(usuario);
  await page.locator('#password').fill(clave);
  await enviar(page);
}

/**
 * Intervalo de 30 s del último código que se usó: Keycloak no acepta el mismo código dos veces. Va en un archivo, no
 * en memoria, porque Playwright reinicia el proceso de las pruebas después de un fallo, y también lo leen las
 * corridas que se hacen una tras otra.
 */
const ARCHIVO_ULTIMO_INTERVALO = join(tmpdir(), 'liv-e2e-ultimo-intervalo-totp');

function ultimoIntervalo(): number {
  try {
    return Number(readFileSync(ARCHIVO_ULTIMO_INTERVALO, 'utf8'));
  } catch {
    return -1;
  }
}

/**
 * Código vigente de una clave TOTP. Espera al intervalo siguiente si el actual está por vencer (para que no cambie
 * entre que se calcula y que Keycloak lo valida) o si su código ya se usó, y amplía el tiempo de la prueba lo que
 * espera.
 */
export async function codigoVigente(page: Page, secreto: string | Buffer = TOTP_SECRETO): Promise<string> {
  const intervalo = () => Math.floor(Date.now() / 30_000);
  const restante = 30_000 - (Date.now() % 30_000);
  if (restante < 3_000 || intervalo() === ultimoIntervalo()) {
    test.info().setTimeout(test.info().timeout + restante + 1_000);
    await page.waitForTimeout(restante + 200);
  }
  writeFileSync(ARCHIVO_ULTIMO_INTERVALO, String(intervalo()));
  return codigoTotp(secreto);
}

/** Escribe el código del segundo factor (el vigente de `e2e.totp` si no se da otro), lo envía y espera la respuesta. */
export async function escribirCodigo(page: Page, codigo?: string): Promise<void> {
  await expect(page.locator('#otp')).toBeVisible();
  await page.locator('#otp').fill(codigo ?? (await codigoVigente(page)));
  await enviar(page);
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
