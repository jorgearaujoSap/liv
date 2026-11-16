import { expect, test, type Locator, type Page } from '@playwright/test';

import { entrarComo, guardarSesionConSegundoFactor, infraccionesDeAccesibilidad } from '../support/acceso';
import { SESION_ADMIN_B, SESION_ADMIN_IPIALES, TOTP_SECRETO, TOTP_SECRETO_B, USUARIOS_E2E } from '../support/entorno';
import {
  comprobarAdministracionesSinVigencias,
  crearVigenciaPorApi,
  fechaLarga,
  leerVigencias,
  postPorApi,
  tabHasta,
  type ListaDeVigencias,
} from '../support/vigencias';

/**
 * Opción 1.4 · Vigencias fiscales (feature 007). Diseño: LIV-Diseño, sección 1.4 (66:1966); los textos son los de sus
 * *frames*: lista (66:1967 a 66:3493), crear (67:3040, 67:3163), mensajes de rechazo (67:3284), vigencia de un año
 * pasado (75:4479) y detalle (68:3460, 68:3846, 68:4235).
 *
 * Todo va en un solo archivo porque las pruebas dependen unas de otras: crean vigencias, que la API no borra, y
 * corren en el orden de este archivo (`preparar.ts` comprueba que las administraciones de prueba parten sin
 * vigencias). Los años no salen del calendario de la prueba, sino de `creatableYears` de la API: el servidor decide
 * cuáles se pueden crear. Las capturas enmascaran los años y las fechas, que cambian con el calendario.
 *
 * Los rechazos V‑02 (007-06 y 007-07) y V‑05 (007-08) no se pueden provocar desde la pantalla, que solo ofrece los años
 * que la API acepta: los demuestran las pruebas del backend. Aquí se demuestra que la pantalla no ofrece otros años y
 * que el botón se inhabilita sin años que crear.
 */

const RUTA = '/opcion/1.4';
type AnioCreable = ListaDeVigencias['creatableYears'][number];

/**
 * Etiqueta de cada año del diálogo según su relación con el año en curso (`relation` del contrato). Figma no trae el
 * texto de `CURRENT`: se exige que diga «en curso» sin ser la de un año anterior ni la de uno siguiente.
 */
const DESCRIPCION_DE_RELACION: Record<string, RegExp> = {
  PAST: /anterior al año en curso/i,
  CURRENT: /^(?!.*(siguiente|anterior)).*en curso/i,
  NEXT: /siguiente al año en curso/i,
};

function dialogo(page: Page): Locator {
  return page.getByRole('dialog', { name: 'Crear vigencia' });
}

function botonCrearPrimera(page: Page): Locator {
  return page.getByRole('button', { name: 'Crear la primera vigencia' });
}

function botonCrearVigencia(page: Page): Locator {
  return page.getByRole('button', { name: 'Crear vigencia' });
}

function botonAbrirPreparacion(page: Page): Locator {
  return page.getByRole('button', { name: 'Abrir preparación' });
}

function tablaDeVigencias(page: Page): Locator {
  return page.getByRole('table');
}

function filasDeVigencias(page: Page): Locator {
  return tablaDeVigencias(page).locator('tbody').getByRole('row');
}

function filaDelAnio(page: Page, year: number): Locator {
  return filasDeVigencias(page).filter({ has: page.getByRole('link', { name: String(year) }) });
}

function datosDeLaVigencia(page: Page): Locator {
  return page.getByRole('region', { name: 'Datos de la vigencia' });
}

function rutaDelDetalle(year: number): RegExp {
  return new RegExp(`${RUTA.replace('.', '\\.')}/${year}$`);
}

/**
 * Lo que cambia con el calendario en la lista y en el diálogo: los años y las fechas de las celdas y de las opciones,
 * lo que dicen el estado vacío y el motivo del botón inhabilitado, y lo que queda detrás del diálogo.
 */
function datosConFecha(page: Page): Locator[] {
  return [
    page.getByRole('cell').filter({ hasText: /\d{4}/ }),
    page.locator('dd').filter({ hasText: /\d{4}/ }),
    dialogo(page).getByRole('group'),
    page.getByText(/^Crea la primera vigencia de tu administración/),
    page.getByText(/^Ya existe la vigencia \d{4}/),
  ];
}

/** En el detalle, además, el encabezado lleva el año («Vigencia 2026») y la ruta de navegación. */
function datosConFechaDelDetalle(page: Page): Locator[] {
  return [...datosConFecha(page), page.locator('liv-page-header')];
}

/** Suelta el foco para que el anillo de un control enfocado por una prueba no quede en la captura de referencia. */
async function sinFoco(page: Page): Promise<void> {
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
}

// Si las administraciones de prueba ya tienen vigencias, se detienen estas pruebas y no las de la 001 (H2‑9).
test.beforeAll(async ({ browser }) => {
  await comprobarAdministracionesSinVigencias(browser);
});

test.describe('1.4 · Sin permiso de consulta y rol de consulta sin vigencias', () => {
  test('007-02 · Un rol sin permiso de consulta (LIQUIDADOR) abre la 1.4 por su URL y ve «Sin permiso»', async ({
    page,
  }) => {
    await entrarComo(page, 'dev.liquidador');

    await page.goto(RUTA);

    await expect(page).toHaveURL(/\/sin-permiso$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('No tienes permiso para ver esta página');
    const api = await page.request.get('/api/v1/fiscal-years');
    expect(api.status()).toBe(403);
  });

  test('007-03 · Un rol de consulta, en una administración sin vigencias, ve el estado vacío sin acciones', async ({
    page,
  }) => {
    await entrarComo(page, 'dev.consulta');

    await page.goto(RUTA);

    await expect(page.getByRole('heading', { level: 1, name: 'Vigencias fiscales' })).toBeVisible();
    await expect(page.getByText('Aún no hay vigencias')).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear/ })).toHaveCount(0);
    await expect(tablaDeVigencias(page)).toHaveCount(0);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
  });
});

test.describe('1.4 · ADMIN_SISTEMA de Ipiales', () => {
  // En serie: cada prueba parte de lo que dejó la anterior, y si una falla las demás se omiten.
  test.describe.configure({ mode: 'serial' });
  test.use({ storageState: SESION_ADMIN_IPIALES });

  let primera: AnioCreable;
  let segunda: AnioCreable;

  test.beforeAll(async ({ browser }) => {
    await guardarSesionConSegundoFactor(browser, USUARIOS_E2E.totp, TOTP_SECRETO, SESION_ADMIN_IPIALES);
  });

  test('007-03 · Sin vigencias, ve el estado vacío con los años que ofrece el servidor y la acción para crear la primera', async ({
    page,
  }) => {
    await page.goto(RUTA);
    const lista = await leerVigencias(page);
    expect(lista.items).toEqual([]);
    expect(lista.canCreate).toBe(true);
    // 007-08: la primera vigencia solo puede ser la del año en curso o la siguiente.
    expect(lista.creatableYears.map((year) => year.relation)).toEqual(['CURRENT', 'NEXT']);
    [primera, segunda] = lista.creatableYears;

    await expect(page.getByRole('heading', { level: 1, name: 'Vigencias fiscales' })).toBeVisible();
    await expect(page.getByText('Aún no hay vigencias')).toBeVisible();
    const descripcion = page.getByText(/^Crea la primera vigencia de tu administración/);
    await expect(descripcion).toContainText(`año en curso (${primera.year})`);
    await expect(descripcion).toContainText(`año siguiente (${segunda.year})`);
    await expect(botonCrearPrimera(page)).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-vacia.png', { fullPage: true, mask: datosConFecha(page) });
  });

  test('007-08 · El diálogo de crear ofrece solo el año en curso y el siguiente, con sus fechas y la advertencia de que no se borra', async ({
    page,
  }) => {
    await page.goto(RUTA);
    await botonCrearPrimera(page).click();

    const crear = dialogo(page);
    await expect(crear).toBeVisible();
    await expect(crear).toContainText('Va del 1 de enero al 31 de diciembre de ese año.');
    const radios = crear.getByRole('radio');
    await expect(radios).toHaveCount(2);
    // Los radios no tienen otro localizador que su orden: es el orden de `creatableYears`, el que se está comprobando.
    for (const [indice, anio] of [primera, segunda].entries()) {
      await expect(radios.nth(indice)).toHaveAccessibleName(String(anio.year));
      await expect(radios.nth(indice)).toHaveAccessibleDescription(DESCRIPCION_DE_RELACION[anio.relation]);
    }
    // La primera viene elegida y sus fechas se derivan del año.
    await expect(radios.nth(0)).toBeChecked();
    await expect(crear.getByText(fechaLarga(primera.startsOn))).toBeVisible();
    await expect(crear.getByText(fechaLarga(primera.endsOn))).toBeVisible();
    await expect(crear.getByText('Una vigencia no se puede borrar')).toBeVisible();
    await expect(crear.getByText(/los años anteriores a la más antigua quedan como históricos/)).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-crear.png', { fullPage: true, mask: datosConFecha(page) });
  });

  test('007-21 · Teclado del grupo de años en el navegador real: las flechas cambian la opción, Tab entra solo a la marcada y el diálogo atrapa el foco', async ({
    page,
  }) => {
    await page.goto(RUTA);
    const abrir = botonCrearPrimera(page);
    await abrir.click();
    const crear = dialogo(page);
    const [primeraOpcion, segundaOpcion] = [crear.getByRole('radio').nth(0), crear.getByRole('radio').nth(1)];
    const cancelar = crear.getByRole('button', { name: 'Cancelar' });
    const confirmar = crear.getByRole('button', { name: 'Crear vigencia' });

    // Al abrir, el foco está en la única parada del grupo: la opción marcada.
    await expect(primeraOpcion).toBeFocused();

    await page.keyboard.press('ArrowDown');
    await expect(segundaOpcion).toBeChecked();
    await expect(segundaOpcion).toBeFocused();
    await page.keyboard.press('ArrowUp');
    await expect(primeraOpcion).toBeChecked();
    await expect(primeraOpcion).toBeFocused();

    // Tab sale del grupo sin pasar por la otra opción, y Mayús+Tab vuelve a la marcada.
    await page.keyboard.press('Tab');
    await expect(cancelar).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(primeraOpcion).toBeFocused();

    // El grupo cuenta como una sola parada: Tab recorre Cancelar y Crear, y la siguiente vuelve a la opción marcada.
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await expect(confirmar).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(primeraOpcion).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(confirmar).toBeFocused();

    // Con la segunda marcada, esa es la parada del grupo.
    await page.keyboard.press('Shift+Tab');
    await page.keyboard.press('Shift+Tab');
    await expect(primeraOpcion).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');
    await expect(segundaOpcion).toBeFocused();

    // Escape cierra el diálogo y el foco vuelve al botón que lo abrió.
    await page.keyboard.press('Escape');
    await expect(crear).toHaveCount(0);
    await expect(abrir).toBeFocused();
  });

  test('007-04 · Crear la primera vigencia la deja Planeada, con la liquidación sin habilitar', async ({ page }) => {
    await page.goto(RUTA);
    await botonCrearPrimera(page).click();
    const crear = dialogo(page);
    await expect(crear.getByRole('radio').nth(0)).toBeChecked();

    await crear.getByRole('button', { name: 'Crear vigencia' }).click();

    await expect(crear).toHaveCount(0);
    const fila = filaDelAnio(page, primera.year);
    await expect(fila).toContainText(fechaLarga(primera.startsOn));
    await expect(fila).toContainText(fechaLarga(primera.endsOn));
    await expect(fila).toContainText('Planeada');
    await expect(fila).toContainText('No habilitada');
    const lista = await leerVigencias(page);
    expect(lista.items.map((vigencia) => vigencia.year)).toEqual([primera.year]);
    expect(lista.items[0].assessmentsEnabled).toBe(false);
  });

  test('007-06 · Con la vigencia N, solo se ofrece N + 1 (el año siguiente) y se puede crear', async ({ page }) => {
    await page.goto(RUTA);
    await botonCrearVigencia(page).click();

    const crear = dialogo(page);
    const radios = crear.getByRole('radio');
    await expect(radios).toHaveCount(1);
    await expect(radios.nth(0)).toHaveAccessibleName(String(segunda.year));
    await expect(radios.nth(0)).toBeChecked();
    await expect(radios.nth(0)).toHaveAccessibleDescription(/siguiente al año en curso/);

    await crear.getByRole('button', { name: 'Crear vigencia' }).click();

    await expect(crear).toHaveCount(0);
    // 007-01: de la más reciente a la más antigua.
    await expect(filasDeVigencias(page)).toHaveCount(2);
    await expect(filasDeVigencias(page).first()).toContainText(String(segunda.year));
    await expect(filasDeVigencias(page).last()).toContainText(String(primera.year));
  });

  test('007-06 · V‑05 · Sin años que crear, «Crear vigencia» queda inhabilitado con su motivo y no se activa con el teclado', async ({
    page,
  }) => {
    await page.goto(RUTA);
    const lista = await leerVigencias(page);
    expect(lista.creatableYears).toEqual([]);

    const crear = botonCrearVigencia(page);
    await expect(crear).toHaveAttribute('aria-disabled', 'true');
    await expect(crear).toHaveAccessibleDescription(
      `Ya existe la vigencia ${segunda.year}, la última que se puede crear.`,
    );

    // El botón sigue recibiendo foco (aria-disabled, no disabled) y ni Enter ni Espacio hacen nada.
    await tabHasta(page, crear);
    await page.keyboard.press('Enter');
    await page.keyboard.press('Space');
    // Pausa fija: sin ella la comprobación de «no pasó nada» se cumpliría antes de que un diálogo tardío apareciera.
    await page.waitForTimeout(300);
    await expect(dialogo(page)).toHaveCount(0);
    await expect(crear).toBeFocused();

    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-lista.png', { fullPage: true, mask: datosConFecha(page) });
  });

  test('007-10 · Abrir la preparación de una vigencia Planeada la pasa a «En preparación»', async ({ page }) => {
    await page.goto(RUTA);
    await filaDelAnio(page, primera.year).getByRole('link').click();

    await expect(page).toHaveURL(rutaDelDetalle(primera.year));
    await expect(page.getByRole('heading', { level: 1, name: `Vigencia ${primera.year}` })).toBeVisible();
    await expect(datosDeLaVigencia(page)).toContainText('Planeada');
    // Detalle Planeada (68:3846): con la acción de abrir la preparación.
    const abrir = botonAbrirPreparacion(page);
    await expect(abrir).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-detalle-planeada.png', {
      fullPage: true,
      mask: datosConFechaDelDetalle(page),
    });

    await abrir.click();

    await expect(datosDeLaVigencia(page)).toContainText('En preparación');
    await expect(abrir).toHaveCount(0);
    // La otra vigencia no cambió: abrir una preparación solo toca la que se abre.
    await page.goto(`${RUTA}/${segunda.year}`);
    await expect(datosDeLaVigencia(page)).toContainText('Planeada');
  });

  test('007-11 · La lista de preparación muestra los nueve puntos, el 1 cumplido y los demás pendientes con «Opción aún no disponible»', async ({
    page,
  }) => {
    await page.goto(`${RUTA}/${primera.year}`);

    const puntos = page.getByRole('region', { name: 'Lista de preparación' }).getByRole('listitem');
    await expect(puntos).toHaveCount(9);
    const punto = (titulo: string): Locator => puntos.filter({ hasText: titulo });
    await expect(punto('Vigencia en preparación')).toContainText('Cumplido');
    await expect(punto('Vigencia en preparación')).toContainText('La vigencia ya está en preparación');
    // Los puntos de las opciones que todavía no existen nunca aparecen cumplidos; 6 y 7 son opcionales.
    for (const titulo of [
      'Resolución de avalúos registrada con su PDF',
      'Catálogo de avalúos publicado para la vigencia',
      'Tarifas por rango registradas y sin solapamientos',
      'Calendario tributario registrado',
      'Beneficios configurados',
      'Reglas de sanción registradas',
      'Prueba de liquidación de muestra sin errores',
      'Configuración de jurisdicción publicada',
    ]) {
      await expect(punto(titulo)).toContainText('Pendiente');
      await expect(punto(titulo)).toContainText('Opción aún no disponible');
    }
    await expect(punto('Beneficios configurados')).toContainText('Opcional');
    await expect(punto('Reglas de sanción registradas')).toContainText('Opcional');
  });

  test('007-12 · V‑03 · «Habilitar liquidación» queda inhabilitado con los puntos que faltan y no se activa con el teclado', async ({
    page,
  }) => {
    await page.goto(`${RUTA}/${primera.year}`);
    const habilitar = page.getByRole('button', { name: 'Habilitar liquidación' });
    await expect(habilitar).toHaveAttribute('aria-disabled', 'true');
    await expect(habilitar).toHaveAccessibleDescription('Faltan 6 puntos obligatorios: 2, 3, 4, 5, 8 y 9.');

    await tabHasta(page, habilitar);
    await page.keyboard.press('Enter');
    await page.keyboard.press('Space');
    // Pausa fija: sin ella la comprobación de «no pasó nada» se cumpliría antes de que una acción tardía se notara.
    await page.waitForTimeout(300);

    await expect(habilitar).toBeFocused();
    await expect(datosDeLaVigencia(page)).toContainText('No habilitada');
    const lista = await leerVigencias(page);
    expect(lista.items.find((vigencia) => vigencia.year === primera.year)?.assessmentsEnabled).toBe(false);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-detalle.png', {
      fullPage: true,
      mask: datosConFechaDelDetalle(page),
    });
  });

  test('007-18 · Una vigencia que no existe en la administración se muestra como «no encontrada»', async ({ page }) => {
    await page.goto(`${RUTA}/${segunda.year + 5}`);

    await expect(page.getByRole('heading', { level: 1, name: 'Vigencia no encontrada' })).toBeVisible();
    await expect(page.getByText('No encontramos esa vigencia')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Volver a las vigencias' })).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
  });
});

test.describe('1.4 · Roles que consultan pero no crean, en una administración con vigencias', () => {
  for (const usuario of ['dev.consulta', 'dev.cargador.tablas']) {
    test(`007-09 · ${usuario} ve las vigencias, de la más reciente a la más antigua, sin las acciones de crear ni de abrir la preparación`, async ({
      page,
    }) => {
      await entrarComo(page, usuario);
      await page.goto(RUTA);
      const lista = await leerVigencias(page);
      expect(lista.canCreate).toBe(false);
      // Precondición: las pruebas del administrador de Ipiales (arriba) crearon las dos vigencias.
      expect(lista.items, 'Corre el archivo completo: las pruebas del administrador crean las vigencias').toHaveLength(2);

      await expect(filasDeVigencias(page)).toHaveCount(2);
      for (const [indice, vigencia] of lista.items.entries()) {
        await expect(filasDeVigencias(page).nth(indice)).toContainText(String(vigencia.year));
        await expect(filasDeVigencias(page).nth(indice)).toContainText(fechaLarga(vigencia.startsOn));
        await expect(filasDeVigencias(page).nth(indice)).toContainText(fechaLarga(vigencia.endsOn));
      }
      await expect(page.getByRole('button', { name: /Crear/ })).toHaveCount(0);

      // La más reciente sigue Planeada (la más antigua se abrió en 007-10): es la que ofrece «Abrir preparación» a quien puede.
      const planeada = lista.items[0];
      await filaDelAnio(page, planeada.year).getByRole('link').click();
      await expect(datosDeLaVigencia(page)).toContainText('Planeada');
      await expect(page.getByRole('region', { name: 'Lista de preparación' })).toBeVisible();
      await expect(botonAbrirPreparacion(page)).toHaveCount(0);
      expect(await infraccionesDeAccesibilidad(page)).toEqual([]);

      // La API tampoco lo permite (403): la pantalla solo oculta, el backend decide.
      expect(await crearVigenciaPorApi(page, planeada.year + 1)).toBe(403);
      expect(await postPorApi(page, `/api/v1/fiscal-years/${planeada.year}/preparation`)).toBe(403);
    });
  }
});

test.describe('1.4 · ADMIN_SISTEMA de la administración de prueba B', () => {
  test.describe.configure({ mode: 'serial' });
  test.use({ storageState: SESION_ADMIN_B });

  let anioDeB: AnioCreable;
  let anioSiguiente: AnioCreable;

  test.beforeAll(async ({ browser }) => {
    await guardarSesionConSegundoFactor(browser, USUARIOS_E2E.adminB, TOTP_SECRETO_B, SESION_ADMIN_B);
  });

  test('CA‑12 · 007-18 · No ve las vigencias de Ipiales: su administración sale de la sesión y empieza vacía', async ({
    page,
  }) => {
    await page.goto(RUTA);

    await expect(page.locator('liv-app-header')).toContainText('Administración de prueba B');
    await expect(page.getByText('Aún no hay vigencias')).toBeVisible();
    const lista = await leerVigencias(page);
    expect(lista.items).toEqual([]);
    [anioDeB, anioSiguiente] = lista.creatableYears;
    // Ofrece el año en curso, el mismo con el que empezó Ipiales: el año no identifica a la administración.
    expect(anioDeB.relation).toBe('CURRENT');
  });

  test('V‑01 · 007-05 · Si otra persona crea el mismo año al mismo tiempo, se rechaza con el mensaje de V‑01 y queda marcado el año que sigue disponible', async ({
    page,
  }) => {
    await page.goto(RUTA);
    await botonCrearPrimera(page).click();
    const crear = dialogo(page);
    await expect(crear.getByRole('radio').nth(0)).toBeChecked();

    // «Otra persona» crea el año marcado mientras el diálogo está abierto (la misma sesión, por la API). Con dos años
    // disponibles el diálogo sigue abierto después del rechazo; con uno solo se cerraría (plan, paso 9a).
    expect(await crearVigenciaPorApi(page, anioDeB.year)).toBe(201);
    await crear.getByRole('button', { name: 'Crear vigencia' }).click();

    const alerta = crear.getByRole('alert');
    await expect(alerta).toContainText('No se creó la vigencia');
    await expect(alerta).toContainText(new RegExp(`V.01 · La vigencia ${anioDeB.year} ya existe en tu administración`));
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    // La lista se volvió a leer: solo queda el año siguiente, ya marcado.
    await expect(crear.getByRole('radio')).toHaveCount(1);
    await expect(crear.getByRole('radio').nth(0)).toBeChecked();
    await expect(crear.getByRole('radio').nth(0)).toHaveAccessibleName(String(anioSiguiente.year));

    await crear.getByRole('button', { name: 'Cancelar' }).click();
    await expect(crear).toHaveCount(0);
  });

  test('007-19 · El mismo año que ya tenía Ipiales existe en B sin conflicto, y su estado es independiente del de Ipiales', async ({
    page,
  }) => {
    await page.goto(RUTA);

    await expect(filasDeVigencias(page)).toHaveCount(1);
    await expect(filaDelAnio(page, anioDeB.year)).toContainText('Planeada');

    // En Ipiales ese año quedó «En preparación» (007-10): abrir la preparación allá no cambió esta.
    await filaDelAnio(page, anioDeB.year).getByRole('link').click();
    await expect(datosDeLaVigencia(page)).toContainText('Planeada');
    await expect(botonAbrirPreparacion(page)).toBeVisible();
  });

  test('CA‑12 · 007-18 · Una vigencia de Ipiales que B no tiene no se puede abrir por su año: se muestra como «no encontrada»', async ({
    page,
  }) => {
    await page.goto(`${RUTA}/${anioSiguiente.year}`);

    await expect(page.getByRole('heading', { level: 1, name: 'Vigencia no encontrada' })).toBeVisible();
    await expect(page.getByText('No encontramos esa vigencia')).toBeVisible();
    const lista = await leerVigencias(page);
    expect(lista.items.map((vigencia) => vigencia.year)).toEqual([anioDeB.year]);
  });

  test('V‑06 · 007-10 · Si otra persona abre la preparación antes, se rechaza con el mensaje de V‑06 y la pantalla se actualiza', async ({
    page,
  }) => {
    await page.goto(`${RUTA}/${anioDeB.year}`);
    const abrir = botonAbrirPreparacion(page);
    await expect(abrir).toBeVisible();

    // «Otra persona» abre la preparación mientras esta pantalla sigue mostrando «Planeada».
    expect(await postPorApi(page, `/api/v1/fiscal-years/${anioDeB.year}/preparation`)).toBe(200);
    await abrir.click();

    const alerta = page.getByRole('alert');
    await expect(alerta).toContainText('No se abrió la preparación');
    await expect(alerta).toContainText(new RegExp(`V.06 · La vigencia ${anioDeB.year} ya no está Planeada`));
    await expect(datosDeLaVigencia(page)).toContainText('En preparación');
    await expect(abrir).toHaveCount(0);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
  });
});
