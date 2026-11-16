import { expect, test, type Locator, type Page } from '@playwright/test';

import { entrarComo, guardarSesionConSegundoFactor, infraccionesDeAccesibilidad } from '../support/acceso';
import { SESION_ADMIN_B, SESION_ADMIN_IPIALES, TOTP_SECRETO, TOTP_SECRETO_B, USUARIOS_E2E } from '../support/entorno';
import {
  aniosDePrueba,
  comprobarAdministracionesSinVigencias,
  crearVigenciaPorApi,
  etiquetaDeAnio,
  fechaCorta,
  fechaLarga,
  leerVigencias,
  postPorApi,
  postPorApiConRespuesta,
  tabHasta,
  type AnioCreable,
  type AniosDePrueba,
} from '../support/vigencias';

/**
 * Opción 1.4 · Vigencias fiscales (feature 007, enmienda 4). Diseño: LIV-Diseño, sección 1.4 (66:1966); los textos son
 * los de sus *frames*: estado vacío (66:2793), sin años disponibles (66:2513), crear (67:3040), crear un año pasado
 * (82:5802), crear con el rechazo V‑01 (67:3163), mensajes de rechazo (67:3284), detalle (68:3460, 68:3846, 68:4235) y
 * el componente `Select` (81:629).
 *
 * Todo va en un solo archivo porque las pruebas dependen unas de otras: crean vigencias, que la API no borra, y
 * corren en el orden de este archivo (`comprobarAdministracionesSinVigencias`, en el `beforeAll`, comprueba que las
 * administraciones de prueba parten sin vigencias). Los años no salen del calendario de la prueba: se toman una sola vez de `creatableYears`, la lista que
 * da el servidor, por su relación con el año en curso y su posición (`aniosDePrueba`). Las capturas enmascaran los
 * años y las fechas, que cambian con el calendario.
 *
 * Los rechazos V‑05 (007-07) no se pueden provocar desde la pantalla, que solo ofrece los años que la API acepta: se
 * prueban por la API, sin crear nada. V‑02 ya no rechaza (enmienda 4): 007-06 comprueba que se crean años en cualquier
 * orden y con huecos.
 */

const RUTA = '/opcion/1.4';
const OPCION_VACIA = 'Elige un año';

function dialogo(page: Page): Locator {
  return page.getByRole('dialog', { name: 'Crear vigencia' });
}

/** La lista desplegable de años del diálogo (`Select`, un `<select>` nativo). */
function selectorDeAnio(page: Page): Locator {
  return dialogo(page).getByRole('combobox', { name: 'Año de la vigencia' });
}

/** Lo que dice cada opción de la lista: la opción vacía y, después, los años en el orden en que los da el servidor. */
function textosDeOpciones(anios: AnioCreable[]): string[] {
  return [OPCION_VACIA, ...anios.map(etiquetaDeAnio)];
}

async function elegirAnio(page: Page, anio: AnioCreable): Promise<void> {
  await selectorDeAnio(page).selectOption({ label: etiquetaDeAnio(anio) });
}

/** «Crear vigencia» dentro del diálogo; el de la página es `botonCrearVigencia`, con el mismo nombre accesible. */
function botonConfirmarCreacion(page: Page): Locator {
  return dialogo(page).getByRole('button', { name: 'Crear vigencia' });
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
    selectorDeAnio(page),
    page.getByText(/^Crea la primera vigencia de tu administración/),
    page.getByText(/^Ya existen todas las vigencias que se pueden crear/),
  ];
}

/** En el detalle, además, el encabezado lleva el año («Vigencia 2026») y la ruta de navegación. */
function datosConFechaDelDetalle(page: Page): Locator[] {
  return [...datosConFecha(page), page.locator('liv-page-header')];
}

/**
 * El detalle mide 1180 px de alto en Figma (68:3460 y 68:3846) y con los datos reales llega a ~1250. La aplicación llena
 * la ventana y desplaza dentro de ella, así que una captura de página completa con 900 px corta la lista: se captura
 * con una ventana más alta, que muestra todo el detalle.
 */
async function conAltoDelDetalle(page: Page): Promise<void> {
  await page.setViewportSize({ width: 1440, height: 1300 });
}

/**
 * Deja la página como la recién abierta para la captura de referencia: sin el foco que le dio una prueba (el anillo
 * saldría en la captura) y sin el desplazamiento que el foco provoca dentro de la ventana de la aplicación.
 */
async function sinFoco(page: Page): Promise<void> {
  await page.evaluate(() => {
    (document.activeElement as HTMLElement | null)?.blur();
    document.querySelectorAll('*').forEach((elemento) => {
      elemento.scrollTop = 0;
    });
  });
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

  // Se toman una sola vez, de la lista de la administración vacía (007-03), y no se vuelven a leer.
  let anios: AniosDePrueba;
  let ofrecidos: AnioCreable[];

  test.beforeAll(async ({ browser }) => {
    await guardarSesionConSegundoFactor(browser, USUARIOS_E2E.totp, TOTP_SECRETO, SESION_ADMIN_IPIALES);
  });

  test('007-03 · Sin vigencias, ve el estado vacío con el rango de años que ofrece el servidor y la acción para crear la primera', async ({
    page,
  }) => {
    await page.goto(RUTA);
    const lista = await leerVigencias(page);
    expect(lista.items).toEqual([]);
    expect(lista.canCreate).toBe(true);
    // Contrato 1.1.0: del más reciente (el año siguiente) al más antiguo, sin repetir ninguno.
    expect(lista.creatableYears.map((year) => year.relation).slice(0, 2)).toEqual(['NEXT', 'CURRENT']);
    expect(lista.creatableYears.slice(2).every((year) => year.relation === 'PAST')).toBe(true);
    const numeros = lista.creatableYears.map((year) => year.year);
    expect(numeros).toEqual([...numeros].sort((a, b) => b - a));
    anios = aniosDePrueba(lista);
    ofrecidos = lista.creatableYears;

    await expect(page.getByRole('heading', { level: 1, name: 'Vigencias fiscales' })).toBeVisible();
    await expect(page.getByText('Aún no hay vigencias')).toBeVisible();
    // El rango sale del último y del primer elemento de la lista, sin enumerar los años.
    const descripcion = page.getByText(/^Crea la primera vigencia de tu administración/);
    await expect(descripcion).toContainText(`cualquier año de ${anios.minimo.year} a ${anios.siguiente.year}`);
    await expect(botonCrearPrimera(page)).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-vacia.png', { fullPage: true, mask: datosConFecha(page) });
  });

  test('007-22 · El diálogo de crear ofrece los años del servidor en una lista, con el año en curso elegido, sus fechas y la advertencia de que no se borra', async ({
    page,
  }) => {
    await page.goto(RUTA);
    await botonCrearPrimera(page).click();

    const crear = dialogo(page);
    await expect(crear).toBeVisible();
    await expect(crear).toContainText('Va del 1 de enero al 31 de diciembre de ese año.');
    const selector = selectorDeAnio(page);
    await expect(selector).toBeVisible();
    // La opción vacía y, después, un año por cada uno de `creatableYears`, en el orden y con el texto del servidor.
    await expect(selector.locator('option')).toHaveText(textosDeOpciones(ofrecidos));
    // El año en curso viene elegido y sus fechas se derivan de él.
    await expect(selector.locator('option:checked')).toHaveText(etiquetaDeAnio(anios.actual));
    await expect(crear.getByText(fechaLarga(anios.actual.startsOn))).toBeVisible();
    await expect(crear.getByText(fechaLarga(anios.actual.endsOn))).toBeVisible();
    await expect(crear.getByText('Una vigencia no se puede borrar', { exact: true })).toBeVisible();
    await expect(crear.getByText('Revisa el año antes de continuar.', { exact: true })).toBeVisible();
    // La advertencia de los años históricos salió con la enmienda 4.
    await expect(crear.getByText(/históricos/)).toHaveCount(0);
    await expect(botonConfirmarCreacion(page)).toBeEnabled();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-crear.png', { fullPage: true, mask: datosConFecha(page) });
  });

  test('007-21 · Teclado del diálogo en el navegador real: el foco inicial está en la lista de años, Enter al abrir no crea nada, Tab da la vuelta dentro del diálogo y Escape devuelve el foco', async ({
    page,
  }) => {
    const peticionesPost: string[] = [];
    page.on('request', (peticion) => {
      if (peticion.method() === 'POST') {
        peticionesPost.push(peticion.url());
      }
    });
    await page.goto(RUTA);
    const abrir = botonCrearPrimera(page);
    await abrir.click();
    const crear = dialogo(page);
    const selector = selectorDeAnio(page);
    const cancelar = crear.getByRole('button', { name: 'Cancelar' });
    const confirmar = botonConfirmarCreacion(page);

    // Al abrir, el foco está en la lista de años y nunca en «Crear vigencia».
    await expect(selector).toBeFocused();

    // Enter al abrir no crea nada: el diálogo sigue abierto y no sale ninguna petición POST.
    await page.keyboard.press('Enter');
    // Pausa fija: sin ella la comprobación de «no pasó nada» se cumpliría antes de que una petición tardía saliera.
    await page.waitForTimeout(300);
    await expect(crear).toBeVisible();
    await expect(selector).toBeFocused();
    expect(peticionesPost).toEqual([]);
    // En Chromium, Enter sobre la lista cerrada abre la de opciones del sistema, que se queda con la siguiente tecla:
    // se cierra devolviendo el foco a la lista, para probar el recorrido del teclado sin esa ventana abierta.
    await selector.evaluate((lista) => lista.blur());
    await selector.focus();
    await expect(selector).toBeFocused();

    // Tab recorre Cancelar y Crear vigencia, y la siguiente vuelve a la lista: el diálogo atrapa el foco.
    await page.keyboard.press('Tab');
    await expect(cancelar).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(confirmar).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(selector).toBeFocused();

    // Mayús+Tab da la vuelta al revés.
    await page.keyboard.press('Shift+Tab');
    await expect(confirmar).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(cancelar).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(selector).toBeFocused();

    // Elegir con el teclado depende de la plataforma: el valor se cambia con `selectOption` y el teclado se usa solo
    // para el foco y para Enter.
    await elegirAnio(page, anios.siguiente);
    await expect(selector.locator('option:checked')).toHaveText(etiquetaDeAnio(anios.siguiente));
    expect(peticionesPost).toEqual([]);

    // Escape cierra el diálogo y el foco vuelve al botón que lo abrió.
    await page.keyboard.press('Escape');
    await expect(crear).toHaveCount(0);
    await expect(abrir).toBeFocused();
  });

  test('007-04 · V‑01 · Crear la vigencia del año en curso la deja Planeada, con la liquidación sin habilitar; un año pasado muestra su advertencia', async ({
    page,
  }) => {
    await page.goto(RUTA);
    await botonCrearPrimera(page).click();
    const crear = dialogo(page);
    const selector = selectorDeAnio(page);
    await expect(selector.locator('option:checked')).toHaveText(etiquetaDeAnio(anios.actual));

    // Con un año pasado elegido, una sola advertencia («Es un año pasado») reemplaza a la general (82:5802).
    await elegirAnio(page, anios.minimo);
    await expect(crear.getByText('Es un año pasado', { exact: true })).toBeVisible();
    await expect(
      crear.getByText('Una vigencia no se puede borrar. Revisa el año antes de continuar.', { exact: true }),
    ).toBeVisible();
    await expect(
      crear.getByText('Solo se podrá liquidar cuando estén cargadas las tablas de ese año.', { exact: true }),
    ).toBeVisible();
    await expect(crear.getByText('Una vigencia no se puede borrar', { exact: true })).toHaveCount(0);
    await expect(crear.getByText(fechaLarga(anios.minimo.startsOn))).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);

    // Con el año en curso, vuelve la advertencia general y la de un año pasado desaparece.
    await elegirAnio(page, anios.actual);
    await expect(crear.getByText('Es un año pasado', { exact: true })).toHaveCount(0);
    await expect(crear.getByText('Una vigencia no se puede borrar', { exact: true })).toBeVisible();

    await botonConfirmarCreacion(page).click();

    await expect(crear).toHaveCount(0);
    // La tabla de consulta muestra las fechas como dd/mm/aaaa; la fecha larga es solo para las fechas sueltas.
    const fila = filaDelAnio(page, anios.actual.year);
    await expect(fila).toContainText(fechaCorta(anios.actual.startsOn));
    await expect(fila).toContainText(fechaCorta(anios.actual.endsOn));
    await expect(fila).toContainText('Planeada');
    await expect(fila).toContainText('No habilitada');
    const lista = await leerVigencias(page);
    expect(lista.items.map((vigencia) => vigencia.year)).toEqual([anios.actual.year]);
    expect(lista.items[0].assessmentsEnabled).toBe(false);
  });

  test('007-06 · V‑02 · Con una vigencia creada, se crean el año siguiente, el más antiguo y uno del medio, en ese orden y con huecos, sin ningún rechazo', async ({
    page,
  }) => {
    await page.goto(RUTA);
    const creados: AnioCreable[] = [anios.actual];
    for (const anio of [anios.siguiente, anios.minimo, anios.intermedio]) {
      await botonCrearVigencia(page).click();
      const crear = dialogo(page);
      const selector = selectorDeAnio(page);
      // Los años que ya existen no se ofrecen, y sin el año en curso ninguno viene elegido ni se puede enviar.
      await expect(selector.locator('option')).toHaveText(
        textosDeOpciones(ofrecidos.filter((ofrecido) => !creados.includes(ofrecido))),
      );
      await expect(selector.locator('option:checked')).toHaveText(OPCION_VACIA);
      await expect(botonConfirmarCreacion(page)).toBeDisabled();

      await elegirAnio(page, anio);
      await botonConfirmarCreacion(page).click();

      await expect(crear).toHaveCount(0);
      await expect(page.getByText(/V.02/)).toHaveCount(0);
      creados.push(anio);
    }

    // 007-01: de la más reciente a la más antigua, aunque se crearon en otro orden.
    const esperados = [anios.siguiente, anios.actual, anios.intermedio, anios.minimo].map((anio) => anio.year);
    const lista = await leerVigencias(page);
    expect(lista.items.map((vigencia) => vigencia.year)).toEqual(esperados);
    await expect(filasDeVigencias(page)).toHaveCount(esperados.length);
    for (const [indice, year] of esperados.entries()) {
      await expect(filasDeVigencias(page).nth(indice)).toContainText(String(year));
    }

    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-lista.png', { fullPage: true, mask: datosConFecha(page) });
  });

  test('V‑05 · 007-07 · Por la API, un año anterior al primero que se puede crear o posterior al último se rechaza con su motivo y su límite, y no cambia nada', async ({
    page,
  }) => {
    await page.goto(RUTA);

    const anterior = await postPorApiConRespuesta(page, '/api/v1/fiscal-years', { year: anios.minimo.year - 1 });
    expect(anterior.estado).toBe(422);
    // El primer año que se puede crear coincide con el último de la lista de la administración vacía.
    expect(anterior.cuerpo).toMatchObject({ code: 'V-05', reason: 'PAST_LIMIT', minYear: anios.minimo.year });

    const posterior = await postPorApiConRespuesta(page, '/api/v1/fiscal-years', { year: anios.siguiente.year + 1 });
    expect(posterior.estado).toBe(422);
    expect(posterior.cuerpo).toMatchObject({ code: 'V-05', reason: 'FUTURE_LIMIT', maxYear: anios.siguiente.year });

    const lista = await leerVigencias(page);
    expect(lista.items).toHaveLength(4);
  });

  test('007-10 · Abrir la preparación de una vigencia Planeada la pasa a «En preparación»', async ({ page }) => {
    await page.goto(RUTA);
    await filaDelAnio(page, anios.actual.year).getByRole('link').click();

    await expect(page).toHaveURL(rutaDelDetalle(anios.actual.year));
    await expect(page.getByRole('heading', { level: 1, name: `Vigencia ${anios.actual.year}` })).toBeVisible();
    await expect(datosDeLaVigencia(page)).toContainText('Planeada');
    // Detalle Planeada (68:3846): con la acción de abrir la preparación.
    const abrir = botonAbrirPreparacion(page);
    await expect(abrir).toBeVisible();
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await conAltoDelDetalle(page);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-detalle-planeada.png', {
      fullPage: true,
      mask: datosConFechaDelDetalle(page),
    });

    await abrir.click();

    await expect(datosDeLaVigencia(page)).toContainText('En preparación');
    await expect(abrir).toHaveCount(0);
    // La otra vigencia no cambió: abrir una preparación solo toca la que se abre.
    await page.goto(`${RUTA}/${anios.siguiente.year}`);
    await expect(datosDeLaVigencia(page)).toContainText('Planeada');
  });

  test('007-11 · La lista de preparación muestra los nueve puntos, el 1 cumplido y los demás pendientes con «Opción aún no disponible»', async ({
    page,
  }) => {
    await page.goto(`${RUTA}/${anios.actual.year}`);

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
    await page.goto(`${RUTA}/${anios.actual.year}`);
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
    expect(lista.items.find((vigencia) => vigencia.year === anios.actual.year)?.assessmentsEnabled).toBe(false);
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    await conAltoDelDetalle(page);
    await sinFoco(page);
    await expect(page).toHaveScreenshot('vigencias-detalle.png', {
      fullPage: true,
      mask: datosConFechaDelDetalle(page),
    });
  });

  test('007-18 · Una vigencia que no existe en la administración se muestra como «no encontrada»', async ({ page }) => {
    await page.goto(`${RUTA}/${anios.siguiente.year + 5}`);

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
      // Precondición: las pruebas del administrador de Ipiales (arriba) crearon sus vigencias. La pantalla se compara con
      // las de la API, no con un número fijo.
      expect(
        lista.items.length,
        'Corre el archivo completo: las pruebas del administrador crean las vigencias',
      ).toBeGreaterThan(0);

      await expect(filasDeVigencias(page)).toHaveCount(lista.items.length);
      for (const [indice, vigencia] of lista.items.entries()) {
        await expect(filasDeVigencias(page).nth(indice)).toContainText(String(vigencia.year));
        await expect(filasDeVigencias(page).nth(indice)).toContainText(fechaCorta(vigencia.startsOn));
        await expect(filasDeVigencias(page).nth(indice)).toContainText(fechaCorta(vigencia.endsOn));
      }
      await expect(page.getByRole('button', { name: /Crear/ })).toHaveCount(0);

      // La más reciente sigue Planeada (la del año en curso se abrió en 007-10): un rol que puede abrirla vería «Abrir
      // preparación»; aquí no aparece.
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

  // Se toman una sola vez, de la lista de la administración vacía, y no se vuelven a leer.
  let aniosB: AniosDePrueba;
  let anioDeB: AnioCreable;
  let anioSiguiente: AnioCreable;
  let ofrecidosB: AnioCreable[];

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
    aniosB = aniosDePrueba(lista);
    ofrecidosB = lista.creatableYears;
    // Ofrece el año en curso, el mismo con el que empezó Ipiales: el año no identifica a la administración.
    ({ actual: anioDeB, siguiente: anioSiguiente } = aniosB);
  });

  test('V‑01 · 007-05 · Si otra persona crea el mismo año al mismo tiempo, se rechaza con el mensaje de V‑01 y, al releer la lista, no queda ningún año elegido', async ({
    page,
  }) => {
    await page.goto(RUTA);
    await botonCrearPrimera(page).click();
    const crear = dialogo(page);
    const selector = selectorDeAnio(page);
    await expect(selector.locator('option:checked')).toHaveText(etiquetaDeAnio(anioDeB));

    // «Otra persona» crea el año elegido mientras el diálogo está abierto (la misma sesión, por la API). Con más años
    // disponibles el diálogo sigue abierto después del rechazo (plan, paso 9a).
    expect(await crearVigenciaPorApi(page, anioDeB.year)).toBe(201);
    await botonConfirmarCreacion(page).click();

    const alerta = crear.getByRole('alert');
    await expect(alerta).toContainText('No se creó la vigencia');
    await expect(alerta).toContainText(
      new RegExp(`V.01 · La vigencia ${anioDeB.year} ya existe en tu administración`),
    );
    expect(await infraccionesDeAccesibilidad(page)).toEqual([]);
    // La lista se volvió a leer: `anioDeB` ya no está entre las opciones, no queda ningún año elegido (el año en
    // curso ya existe), «Crear vigencia» está inhabilitado y el foco está en la lista (67:3163).
    await expect(selector.locator('option')).toHaveText(
      textosDeOpciones(ofrecidosB.filter((ofrecido) => ofrecido !== anioDeB)),
    );
    await expect(selector.locator('option:checked')).toHaveText(OPCION_VACIA);
    await expect(botonConfirmarCreacion(page)).toBeDisabled();
    await expect(selector).toBeFocused();

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

  // Va al final de la serie de B porque la deja sin años que crear: las pruebas anteriores no cambian, y
  // `limpiar-vigencias.sh` borra todo antes de la corrida siguiente.
  test('V‑05 · 007-23 · Con todas las vigencias creadas, «Crear vigencia» queda inhabilitado con su motivo, no se activa con el teclado y pasa axe', async ({
    page,
  }) => {
    // Crea de uno en uno los años que le faltan a B, de `minimo` a `anioSiguiente`.
    test.setTimeout(120_000);
    await page.goto(RUTA);
    const existentes = new Set((await leerVigencias(page)).items.map((vigencia) => vigencia.year));
    for (let year = aniosB.minimo.year; year <= anioSiguiente.year; year++) {
      if (!existentes.has(year)) {
        expect(await crearVigenciaPorApi(page, year), `crear la vigencia ${year}`).toBe(201);
      }
    }
    await page.goto(RUTA);
    expect((await leerVigencias(page)).creatableYears).toEqual([]);

    const crear = botonCrearVigencia(page);
    await expect(crear).toHaveAttribute('aria-disabled', 'true');
    await expect(crear).toHaveAccessibleDescription(
      `Ya existen todas las vigencias que se pueden crear: de ${aniosB.minimo.year} a ${anioSiguiente.year}.`,
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
    await expect(page).toHaveScreenshot('vigencias-sin-anios.png', { fullPage: true, mask: datosConFecha(page) });
  });
});
