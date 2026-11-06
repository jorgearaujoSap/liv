import { expect, test } from '@playwright/test';

import { enPaginaDeAcceso, entrarComo } from '../support/acceso';

/**
 * El aviso de inactividad (001-22) y la expiración (001-10) en un navegador. Con los 15 minutos reales la prueba
 * duraría más de un cuarto de hora, así que corre solo con el BFF levantado con una sesión corta
 * (`SPRING_SESSION_TIMEOUT=150s`) y `LIV_E2E_SESION_CORTA=1` (README › «Aviso de inactividad»). El frontend avisa
 * 2 minutos antes de que venza: con 150 s, a los 30 s de la última actividad.
 */
const SESION_CORTA = process.env.LIV_E2E_SESION_CORTA === '1';

test.describe('Aviso de inactividad y expiración, con una sesión corta del BFF', () => {
  test.skip(!SESION_CORTA, 'Necesita el BFF con SPRING_SESSION_TIMEOUT=150s y LIV_E2E_SESION_CORTA=1');

  test('001-22 · Antes de que venza avisa, y «Seguir conectado» extiende la sesión sin salir de la página', async ({
    page,
  }) => {
    test.setTimeout(150_000);
    await entrarComo(page, 'dev.consulta');

    const aviso = page.getByRole('dialog', { name: 'Tu sesión está por expirar' });
    await expect(aviso).toBeVisible({ timeout: 60_000 });
    await expect(aviso).toContainText('por inactividad');
    await expect(aviso.getByRole('button', { name: 'Seguir conectado' })).toBeFocused();

    await aviso.getByRole('button', { name: 'Seguir conectado' }).click();

    await expect(aviso).toBeHidden();
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Hola,');
    const estado = await page.evaluate(async () => (await fetch('/bff/session')).json());
    expect(estado.idleSecondsLeft, 'la sesión volvió a empezar').toBeGreaterThan(120);
  });

  test('001-22 · «Cerrar sesión» en el aviso cierra la sesión y lleva a iniciar sesión', async ({ page }) => {
    test.setTimeout(150_000);
    await entrarComo(page, 'dev.consulta');

    const aviso = page.getByRole('dialog', { name: 'Tu sesión está por expirar' });
    await expect(aviso).toBeVisible({ timeout: 60_000 });
    await aviso.getByRole('button', { name: 'Cerrar sesión' }).click();

    await enPaginaDeAcceso(page);
  });

  test('001-10 · Sin actividad, la sesión vence y la página lleva a iniciar sesión', async ({ page }) => {
    test.setTimeout(240_000);
    await entrarComo(page, 'dev.consulta');

    await expect(page.getByRole('dialog', { name: 'Tu sesión está por expirar' })).toBeVisible({
      timeout: 60_000,
    });

    // Nadie responde el aviso: a los 150 s de la última actividad la sesión vence.
    await enPaginaDeAcceso(page, 180_000);
    const respuesta = await page.request.get('/api/v1/me');
    expect(respuesta.status(), 'la sesión vieja ya no sirve').toBe(401);
  });
});
