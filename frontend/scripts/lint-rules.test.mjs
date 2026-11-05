// Pruebas de las reglas de frontera de eslint.config.js (T20, feature 001; doc. 04 §3.2: «HttpClient
// solo en servicios de datos»). Los componentes y el resto de la aplicación llaman a la API por el
// cliente generado de src/app/api/, nunca por HttpClient.
import assert from 'node:assert/strict';
import { dirname, join } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

import { ESLint } from 'eslint';

const projectDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const eslint = new ESLint({ cwd: projectDir });

const restricted = async (code, filePath) => {
  const [result] = await eslint.lintText(code, { filePath: join(projectDir, filePath) });
  return result.messages.filter((message) => message.ruleId === 'no-restricted-imports');
};

const IMPORT_HTTP_CLIENT = `import { HttpClient } from '@angular/common/http';\nexport const x = HttpClient;\n`;

describe('HttpClient solo en el cliente generado', () => {
  for (const filePath of [
    'src/app/features/home/home.ts',
    'src/app/core/session.service.ts',
    'src/app/core/data/session-data.service.ts',
  ]) {
    it(`${filePath} no puede importar HttpClient`, async () => {
      const messages = await restricted(IMPORT_HTTP_CLIENT, filePath);
      assert.equal(messages.length, 1);
      assert.match(messages[0].message, /HttpClient/);
      assert.match(messages[0].message, /src\/app\/api/);
    });
  }

  it('tampoco con un alias', async () => {
    const aliased = `import { HttpClient as Cliente } from '@angular/common/http';\nexport const x = Cliente;\n`;
    assert.equal((await restricted(aliased, 'src/app/core/a.ts')).length, 1);
  });

  it('src/app/api/ (cliente generado) sí puede usarlo', async () => {
    assert.deepEqual(await restricted(IMPORT_HTTP_CLIENT, 'src/app/api/core/core-api.ts'), []);
  });

  it('el resto de @angular/common/http sigue permitido (interceptores, provideHttpClient)', async () => {
    const code = `import { provideHttpClient, type HttpInterceptorFn } from '@angular/common/http';\nexport const x: HttpInterceptorFn | undefined = undefined;\nexport const y = provideHttpClient;\n`;
    assert.deepEqual(await restricted(code, 'src/app/core/interceptor.ts'), []);
  });
});

describe('las fronteras con liv-components y con los otros proyectos siguen vigentes', () => {
  it('liv-components solo por su API pública', async () => {
    const internal = `import { x } from 'liv-components/fesm2022/liv-components';\nexport const y = x;\n`;
    assert.equal((await restricted(internal, 'src/app/core/a.ts')).length, 1);
    const publicApi = `import { x } from 'liv-components';\nexport const y = x;\n`;
    assert.deepEqual(await restricted(publicApi, 'src/app/core/a.ts'), []);
  });

  it('no se importan archivos de ../backend ni de ../ui', async () => {
    const backend = `import { x } from '../../../backend/contracts/openapi/core';\nexport const y = x;\n`;
    assert.equal((await restricted(backend, 'src/app/core/a.ts')).length, 1);
    const ui = `import { x } from '../../../ui/projects/liv-components/src/public-api';\nexport const y = x;\n`;
    assert.equal((await restricted(ui, 'src/app/core/a.ts')).length, 1);
  });
});
