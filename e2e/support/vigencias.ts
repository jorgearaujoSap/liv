import { chromium, type Locator, type Page } from '@playwright/test';

import { entrarComo } from './acceso';
import { BASE_URL } from './entorno';

/** Lo que usan las pruebas de la respuesta de `GET /api/v1/fiscal-years` (contrato `tax`); el resto se ignora. */
export interface ListaDeVigencias {
  items: { year: number; startsOn: string; endsOn: string; status: string; assessmentsEnabled: boolean }[];
  canCreate: boolean;
  creatableYears: { year: number; startsOn: string; endsOn: string; relation: string }[];
}

/** Lee la lista de vigencias de la administración de la sesión abierta en `page`, por la API y con su cookie. */
export async function leerVigencias(page: Page): Promise<ListaDeVigencias> {
  const respuesta = await page.request.get('/api/v1/fiscal-years');
  if (!respuesta.ok()) {
    throw new Error(`GET /api/v1/fiscal-years respondió ${respuesta.status()}: ${await respuesta.text()}`);
  }
  return (await respuesta.json()) as ListaDeVigencias;
}

/** `GET /api/v1/fiscal-years` responde 404 mientras la API de la feature 007 no está desplegada en el entorno. */
async function apiDeVigenciasDesplegada(page: Page): Promise<boolean> {
  return (await page.request.get('/api/v1/fiscal-years')).status() !== 404;
}

/**
 * Usuarios de consulta de cada administración de la semilla, sin segundo factor: sirven para mirar qué vigencias
 * hay sin consumir un código TOTP ni crear nada.
 */
const OBSERVADORES = [
  { usuario: 'dev.consulta', administracion: 'el Municipio de Ipiales' },
  { usuario: 'dev.consulta.b', administracion: 'la administración de prueba B' },
] as const;

/**
 * Comprobación previa de la feature 007 (H2‑9): las pruebas parten de administraciones sin vigencias, porque una
 * vigencia no se puede borrar por la API. Devuelve una frase por cada administración que ya tiene vigencias, con sus
 * años; vacío si las dos están limpias.
 */
export async function administracionesConVigencias(): Promise<string[]> {
  const navegador = await chromium.launch();
  try {
    const encontradas: string[] = [];
    for (const { usuario, administracion } of OBSERVADORES) {
      const contexto = await navegador.newContext({ baseURL: BASE_URL });
      try {
        const page = await contexto.newPage();
        await entrarComo(page, usuario);
        if (!(await apiDeVigenciasDesplegada(page))) {
          // Sin la API no puede haber vigencias de una corrida anterior; las pruebas de la 1.4 fallarán por su criterio.
          return [];
        }
        const { items } = await leerVigencias(page);
        if (items.length > 0) {
          encontradas.push(`${administracion} (${items.map((vigencia) => vigencia.year).join(', ')})`);
        }
      } finally {
        await contexto.close();
      }
    }
    return encontradas;
  } finally {
    await navegador.close();
  }
}

/** Mensaje que detiene `preparar.ts` si quedan vigencias de una corrida anterior. */
export function mensajeDeVigenciasPrevias(administraciones: string[]): string {
  return (
    `Ya hay vigencias fiscales en ${administraciones.join(' y en ')}. Las pruebas de la opción 1.4 crean las suyas y ` +
    'una vigencia no se puede borrar por la API: limpia las del entorno local con ' +
    'sh dev/limpiar-vigencias.sh (desde backend/) y vuelve a correr las pruebas.'
  );
}

/** Fecha de negocio (`2026-01-01`) como la escribe el diseño: «1 de enero de 2026». No depende del reloj ni de la zona. */
export function fechaLarga(fecha: string): string {
  return new Date(`${fecha}T00:00:00Z`).toLocaleDateString('es-CO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  });
}

/**
 * Envía un POST a la API con la sesión de `page`, con el token anti-CSRF de la cookie `XSRF-TOKEN`, igual que el
 * frontend. Devuelve el estado HTTP, sin interpretar la respuesta.
 */
export async function postPorApi(page: Page, ruta: string, cuerpo?: unknown): Promise<number> {
  return page.evaluate(
    async ({ ruta: destino, cuerpo: contenido }) => {
      const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
      if (!cookie) {
        throw new Error('No hay cookie XSRF-TOKEN: carga una página de LIV antes de llamar a la API.');
      }
      const respuesta = await fetch(destino, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': decodeURIComponent(cookie.slice(cookie.indexOf('=') + 1)),
        },
        body: contenido === undefined ? undefined : JSON.stringify(contenido),
      });
      return respuesta.status;
    },
    { ruta, cuerpo },
  );
}

/** Crea una vigencia por la API, como lo haría «otra persona» al mismo tiempo (V‑01). */
export async function crearVigenciaPorApi(page: Page, year: number): Promise<number> {
  return postPorApi(page, '/api/v1/fiscal-years', { year });
}

/** Pulsa Tab hasta que el foco llega a `elemento`; falla si no llega en `maximo` pulsaciones. */
export async function tabHasta(page: Page, elemento: Locator, maximo = 60): Promise<void> {
  for (let i = 0; i < maximo; i++) {
    await page.keyboard.press('Tab');
    if (await elemento.evaluate((nodo) => nodo === document.activeElement)) {
      return;
    }
  }
  throw new Error(`El foco no llegó al elemento con ${maximo} pulsaciones de Tab.`);
}
