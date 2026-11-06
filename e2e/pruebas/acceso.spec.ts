import { expect, test } from '@playwright/test';

import {
  enPaginaDeAcceso,
  escribirCodigo,
  infraccionesDeAccesibilidad,
  ingresar,
} from '../support/acceso';
import { USUARIOS_E2E } from '../support/entorno';

const MENSAJE_UNICO =
  'Usuario o contraseña incorrectos. Tras varios intentos fallidos el acceso se bloquea por 15 minutos.';

test.describe('Página de acceso (tema liv de Keycloak)', () => {
  test('001-01 · Sin sesión, una ruta de LIV lleva a iniciar sesión y, al entrar, vuelve a esa ruta', async ({
    page,
  }) => {
    await page.goto('/opcion/1.4');
    await ingresar(page, 'dev.consulta');

    await expect(page).toHaveURL(/\/opcion\/1\.4$/);
  });

  test('001-19 · La página de acceso está en español, con los estilos de LIV, y cumple WCAG 2.1 AA', async ({
    page,
  }) => {
    await page.goto('/');
    await enPaginaDeAcceso(page);

    await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'es');
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await expect(page).toHaveScreenshot('acceso.png', { fullPage: true });
  });

  test('001-19 · «Mostrar contraseña» la deja ver y la vuelve a ocultar', async ({ page }) => {
    await page.goto('/');
    await enPaginaDeAcceso(page);
    const clave = page.locator('#password');
    await clave.fill('una-clave');

    await page.getByRole('button', { name: /mostrar contraseña/i }).click();
    await expect(clave).toHaveAttribute('type', 'text');
    await page.getByRole('button', { name: /ocultar contraseña/i }).click();
    await expect(clave).toHaveAttribute('type', 'password');
  });

  test('001-03 · Una contraseña incorrecta y un usuario inexistente dan el mismo mensaje', async ({ page }) => {
    await page.goto('/');
    await ingresar(page, 'dev.consulta', 'clave-equivocada');
    await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);

    await ingresar(page, 'usuario.que.no.existe', 'clave-equivocada');
    await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);
  });

  test('001-05 · Un usuario desactivado no entra y ve el mismo mensaje', async ({ page }) => {
    await page.goto('/');
    await ingresar(page, 'dev.desactivado');

    await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);
    await expect(page).not.toHaveURL(/localhost:4200/);
  });

  test('001-08 · Con un rol que no exige segundo factor, entra sin él', async ({ page }) => {
    await page.goto('/');
    await ingresar(page, 'dev.liquidador');

    await expect(page.locator('#otp')).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola,');
  });

  test('001-07 · Con segundo factor, pide el código; uno incorrecto no deja entrar y el correcto sí', async ({
    page,
  }) => {
    await page.goto('/');
    await ingresar(page, USUARIOS_E2E.totp);

    await escribirCodigo(page, '000000');
    await expect(page.getByText('El código no es válido.')).toBeVisible();
    await expect(page.locator('#otp')).toHaveAttribute('aria-invalid', 'true');

    await escribirCodigo(page);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola, Tomás');
  });
});

test.describe('Bloqueo (001-04)', () => {
  test('001-04 · Tras 5 fallos seguidos, ni la contraseña correcta deja entrar, y el mensaje es el mismo', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await page.goto('/');
    for (let intento = 0; intento < 5; intento++) {
      await ingresar(page, USUARIOS_E2E.bloqueo, 'clave-equivocada');
      await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);
      // Más de 1 s entre intentos, para no activar la protección contra intentos automáticos de Keycloak.
      await page.waitForTimeout(2_000);
    }

    await ingresar(page, USUARIOS_E2E.bloqueo);
    await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);
    await expect(page).not.toHaveURL(/localhost:4200/);
  });
});
