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

  test('001-02 · Solo el área de trabajo se desplaza: la página no tiene su propia barra ni espacio debajo', async ({
    page,
  }) => {
    await entrarComo(page, 'dev.consulta');

    // Un contenido más alto que la ventana, con al final un elemento de posición absoluta, como el aviso oculto
    // (`sr-only`) de cada `liv-button`: su bloque contenedor es la página, y `overflow` del área de trabajo no lo recorta.
    const medidas = await page.evaluate(() => {
      const trabajo = document.querySelector('main') as HTMLElement;
      const relleno = document.createElement('div');
      relleno.style.height = `${window.innerHeight * 2}px`;
      const final = document.createElement('div');
      final.style.marginTop = `${window.innerHeight * 1.5}px`;
      const oculto = document.createElement('span');
      oculto.style.cssText = 'position:absolute;width:1px;height:1px';
      final.append(oculto);
      relleno.append(final);
      trabajo.append(relleno);
      const pagina = document.documentElement;
      return {
        altoPagina: pagina.scrollHeight,
        altoVentana: window.innerHeight,
        trabajoSeDesplaza: trabajo.scrollHeight > trabajo.clientHeight,
      };
    });

    expect(medidas.trabajoSeDesplaza).toBe(true);
    expect(medidas.altoPagina).toBe(medidas.altoVentana);
  });

  test('001-02 · Las tarjetas de módulos miden todas lo mismo, como en Figma', async ({ page }) => {
    await entrarComo(page, 'dev.consulta');

    const altos = await page
      .locator('liv-module-card .module-card__link')
      .evaluateAll((tarjetas) => tarjetas.map((t) => Math.round(t.getBoundingClientRect().height)));
    expect(altos.length).toBeGreaterThan(1);
    expect(new Set(altos).size).toBe(1);
  });

  test('001-15 · El menú muestra solo lo que permite el rol, con el tercer nivel y la 1.4 ya abierta', async ({ page }) => {
    await entrarComoAdministrador(page);
    const menu = page.locator('liv-side-nav');

    await menu.getByRole('button', { name: 'Administración' }).click();
    await expect(menu.getByText('Usuarios, roles y permisos')).toBeVisible();
    await expect(menu.locator('liv-nav-item[data-type="subgroup"]')).toHaveCount(2);
    // Con la feature 007, «Vigencias fiscales» (1.4) ya navega; las demás opciones siguen en «Próximamente».
    const vigencias = menu.getByRole('link', { name: 'Vigencias fiscales' });
    await expect(vigencias).toHaveAttribute('href', /\/opcion\/1\.4$/);
    await expect(vigencias).not.toHaveAttribute('aria-disabled', 'true');
    await expect(menu.getByRole('link', { name: 'Referencias vehiculares normalizadas' })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
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

    // 1.7 (referencias vehiculares): la lee `CONSULTA` y todavía no la construye ninguna feature. La 1.4 ya existe.
    await page.goto('/opcion/1.7');

    await expect(page).toHaveURL(/\/proximamente/);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await expect(page).toHaveScreenshot('proximamente.png', { fullPage: true });
  });
});
