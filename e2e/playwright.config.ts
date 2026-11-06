import { defineConfig, devices } from '@playwright/test';

import { BASE_URL } from './support/entorno';

/**
 * Pruebas de navegador de LIV contra el entorno local de desarrollo (ADR 0030): el sistema se levanta como dice
 * `README.md` › «Ejecutar LIV en local» y estas pruebas lo usan solo por HTTP.
 *
 * Una sola tarea a la vez: los usuarios que crea `preparar.ts` y la sesión del BFF son compartidos.
 */
export default defineConfig({
  testDir: './pruebas',
  globalSetup: './preparar.ts',
  globalTeardown: './limpiar.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: {
    timeout: 10_000,
    // Las capturas se comparan con una tolerancia mínima: un cambio visual real las rompe.
    toHaveScreenshot: { maxDiffPixelRatio: 0.01, animations: 'disabled' },
  },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'informe' }]],
  outputDir: 'resultados',
  use: {
    baseURL: BASE_URL,
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
});
