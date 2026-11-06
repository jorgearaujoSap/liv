import { expect, test, type Page } from '@playwright/test';

import { entrarComo, escribirCodigo, infraccionesDeAccesibilidad, ingresar } from '../support/acceso';
import { USUARIOS_E2E } from '../support/entorno';

async function entrarComoAdministrador(page: Page): Promise<void> {
  await page.goto('/');
  await ingresar(page, USUARIOS_E2E.totp);
  await escribirCodigo(page);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola, Tomás');
}

test.describe('Inicio y estructura general', () => {
  test('001-02 · Inicio muestra en la cabecera el nombre, la administración y el rol', async ({ page }) => {
    await entrarComo(page, 'dev.consulta');

    const cabecera = page.locator('liv-app-header');
    await expect(cabecera).toContainText('Luis Moreno Prueba');
    await expect(cabecera).toContainText('Municipio de Ipiales');
    await expect(cabecera).toContainText('Consulta');
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await expect(page).toHaveScreenshot('inicio-consulta.png', { fullPage: true });
  });

  test('001-02 · Las tarjetas de módulos miden todas lo mismo, como en Figma', async ({ page }) => {
    await entrarComo(page, 'dev.consulta');

    const altos = await page
      .locator('liv-module-card .module-card__link')
      .evaluateAll((tarjetas) => tarjetas.map((t) => Math.round(t.getBoundingClientRect().height)));
    expect(altos.length).toBeGreaterThan(1);
    expect(new Set(altos).size).toBe(1);
  });

  test('001-15 · El menú muestra solo lo que permite el rol, con el tercer nivel en 1.5 y 1.6', async ({ page }) => {
    await entrarComoAdministrador(page);
    const menu = page.locator('liv-side-nav');

    await menu.getByRole('button', { name: 'Administración' }).click();
    await expect(menu.getByText('Usuarios, roles y permisos')).toBeVisible();
    await expect(menu.locator('liv-nav-item[data-type="subgroup"]')).toHaveCount(2);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await expect(page).toHaveScreenshot('inicio-administrador-menu.png', { fullPage: true });
  });

  test('001-15 · Un rol de consulta no ve las opciones que no le corresponden', async ({ page }) => {
    await entrarComo(page, 'dev.consulta');
    const menu = page.locator('liv-side-nav');

    await menu.getByRole('button', { name: 'Administración' }).click();
    await expect(menu.getByText('Usuarios, roles y permisos')).toHaveCount(0);
    await expect(menu.getByText('Vigencias fiscales')).toBeVisible();
  });

  test('001-16 · Una opción sin permiso, abierta por URL, muestra «Sin permiso»', async ({ page }) => {
    await entrarComo(page, 'dev.consulta');

    await page.goto('/opcion/1.2');

    await expect(page).toHaveURL(/\/sin-permiso$/);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await expect(page).toHaveScreenshot('sin-permiso.png', { fullPage: true });
  });

  test('001-15 · Una opción que aún no está construida muestra «Próximamente»', async ({ page }) => {
    await entrarComo(page, 'dev.consulta');

    await page.goto('/opcion/1.4');

    await expect(page).toHaveURL(/\/proximamente/);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await expect(page).toHaveScreenshot('proximamente.png', { fullPage: true });
  });
});
