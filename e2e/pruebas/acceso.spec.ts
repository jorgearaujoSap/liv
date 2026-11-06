import { expect, test } from '@playwright/test';

import {
  codigoVigente,
  enPaginaDeAcceso,
  enviar,
  escribirCodigo,
  infraccionesDeAccesibilidad,
  ingresar,
} from '../support/acceso';
import { KEYCLOAK_URL, TOTP_SECRETO, USUARIOS_E2E } from '../support/entorno';
import { codigoIncorrecto, codigoTotp, desdeBase32 } from '../support/totp';

const MENSAJE_UNICO =
  'Usuario o contraseña incorrectos. Tras varios intentos fallidos el acceso se bloquea por 15 minutos.';

/** Sigue en Keycloak: no volvió a LIV con una sesión. */
const EN_KEYCLOAK = new RegExp(`^${KEYCLOAK_URL}/`);

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
    // Su contraseña es la misma de los demás usuarios de desarrollo, con la que 001-08 entra.
    await page.goto('/');
    await ingresar(page, 'dev.desactivado');

    await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);
    await expect(page).toHaveURL(EN_KEYCLOAK);
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

    await escribirCodigo(page, codigoIncorrecto(codigoTotp(TOTP_SECRETO)));
    await expect(page.getByText('El código no es válido.')).toBeVisible();
    await expect(page.locator('#otp')).toHaveAttribute('aria-invalid', 'true');

    await escribirCodigo(page);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola, Tomás');
  });

  test('001-06 · En su primer ingreso, un rol que lo exige configura el segundo factor antes de entrar', async ({
    page,
  }) => {
    await page.goto('/');
    await ingresar(page, USUARIOS_E2E.alta);

    await expect(page.getByRole('heading', { name: 'Activa la verificación en dos pasos' })).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    // La misma clave que una persona escribiría a mano en la aplicación de autenticación.
    const secreto = desdeBase32(await page.locator('#kc-totp-secret-key').innerText());

    await page.locator('#totp').fill(codigoIncorrecto(codigoTotp(secreto)));
    await enviar(page, '#saveTOTPBtn');
    await expect(page.getByText('El código no es válido. Escribe el que muestra ahora la aplicación.')).toBeVisible();

    await page.locator('#totp').fill(await codigoVigente(page, secreto));
    await enviar(page, '#saveTOTPBtn');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola, Ana');
  });
});

test.describe('Bloqueo (001-04)', () => {
  test('001-04 · Tras 5 fallos seguidos, ni la contraseña correcta deja entrar, y el mensaje es el mismo', async ({
    page,
    context,
  }) => {
    test.setTimeout(120_000);
    // Antes de bloquearlo: con la contraseña correcta entra.
    await page.goto('/');
    await ingresar(page, USUARIOS_E2E.bloqueo);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola, Berta');
    await context.clearCookies();

    await page.goto('/');
    for (let intento = 0; intento < 5; intento++) {
      await ingresar(page, USUARIOS_E2E.bloqueo, 'clave-equivocada');
      await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);
      // Más de 1 s entre intentos, para no activar la protección contra intentos automáticos de Keycloak.
      await page.waitForTimeout(2_000);
    }

    await ingresar(page, USUARIOS_E2E.bloqueo);
    await expect(page.locator('#liv-alert-message')).toContainText(MENSAJE_UNICO);
    await expect(page).toHaveURL(EN_KEYCLOAK);
  });
});
