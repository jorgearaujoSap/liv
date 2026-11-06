import { expect, test } from '@playwright/test';

import { enPaginaDeAcceso, entrarComo } from '../support/acceso';

test.describe('Sesión en el navegador', () => {
  test('001-11 · El navegador no guarda ningún token: solo la cookie de sesión, HttpOnly y SameSite=Strict', async ({
    page,
    context,
  }) => {
    await entrarComo(page, 'dev.consulta');

    const almacenado = await page.evaluate(() => ({
      local: JSON.stringify(localStorage),
      sesion: JSON.stringify(sessionStorage),
    }));
    expect(almacenado.local).not.toMatch(/eyJ/);
    expect(almacenado.sesion).not.toMatch(/eyJ/);

    const cookies = await context.cookies('http://localhost:4200');
    for (const cookie of cookies) {
      expect(cookie.value, cookie.name).not.toMatch(/^eyJ/);
    }
    const sesion = cookies.find((cookie) => cookie.name === 'LIV_SESSION');
    expect(sesion?.httpOnly).toBe(true);
    expect(sesion?.sameSite).toBe('Strict');
  });

  test('001-09 · «Salir» cierra la sesión, la URL de cierre no lleva tokens y «atrás» no muestra datos', async ({
    page,
  }) => {
    await entrarComo(page, 'dev.consulta');
    const navegaciones: string[] = [];
    page.on('framenavigated', (frame) => {
      if (frame === page.mainFrame()) {
        navegaciones.push(frame.url());
      }
    });

    await page.getByRole('button', { name: 'Salir' }).click();
    await enPaginaDeAcceso(page);
    for (const url of navegaciones) {
      expect(url).not.toMatch(/id_token|access_token|refresh_token|eyJ/);
    }

    await page.goBack();
    await enPaginaDeAcceso(page);
    await expect(page.getByText('Hola,')).toHaveCount(0);
  });

  test('001-12 · Una petición que modifica datos sin el token anti-CSRF se rechaza con 403 y code 001-12', async ({
    page,
  }) => {
    await entrarComo(page, 'dev.consulta');

    const respuesta = await page.evaluate(async () => {
      const r = await fetch('/api/v1/me', { method: 'POST' });
      return { status: r.status, cuerpo: await r.text() };
    });

    expect(respuesta.status).toBe(403);
    expect(JSON.parse(respuesta.cuerpo)).toMatchObject({ code: '001-12' });
  });
});
