// Tests of the idempotent publish (feature 002, 002-06 and 002-08). npm is simulated: no test reaches the
// registry, and the only publish a test can run is the simulated one.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

import { integrityOf, main, publish } from './release-publish.mjs';
import { fakeFiles, fakeRun, npmError, result } from './test-helpers.mjs';

const TARBALL = 'liv-components-0.6.0.tgz';
const CONTENT = Buffer.from('contenido del paquete verificado');
const SAME = integrityOf(CONTENT);
const OTHER = integrityOf(Buffer.from('otro contenido'));
const VIEW = 'npm view liv-components@0.6.0 dist.integrity --json --prefer-online';
const PUBLISH = `npm publish ${TARBALL} --ignore-scripts --access public`;
const ARGS = [TARBALL, 'liv-components', '0.6.0'];

function setup(view, publishResult = result(0, '+ liv-components@0.6.0')) {
  const { run, calls } = fakeRun({
    [VIEW]: view,
    [PUBLISH]: publishResult,
    [`${PUBLISH} --dry-run`]: publishResult,
  });
  return { run, calls, readFile: fakeFiles({ [TARBALL]: CONTENT }) };
}

describe('release-publish · no publica dos veces (002-06)', () => {
  it('la versión ya existe con la misma integridad: sigue sin publicar', () => {
    const { run, calls, readFile } = setup(result(0, JSON.stringify(SAME)));
    assert.match(
      publish({ argv: ARGS, run, readFile }),
      /misma integridad: no se publica otra vez/,
    );
    assert.deepEqual(calls, [VIEW]);
  });

  it('la versión ya existe con otra integridad: falla sin publicar', () => {
    const { run, calls, readFile } = setup(result(0, JSON.stringify(OTHER)));
    assert.throws(
      () => publish({ argv: ARGS, run, readFile }),
      /ya está publicada con otra integridad/,
    );
    assert.deepEqual(calls, [VIEW]);
  });

  it('npm responde E404: publica el .tgz con --ignore-scripts y --access public', () => {
    const { run, calls, readFile } = setup(npmError('E404', 'No match found for version 0.6.0'));
    assert.match(publish({ argv: ARGS, run, readFile }), /^Publicada liv-components@0\.6\.0/);
    assert.deepEqual(calls, [VIEW, PUBLISH]);
  });

  it('con --dry-run, solo ejecuta npm publish --dry-run y lo dice', () => {
    const { run, calls, readFile } = setup(npmError('E404'));
    assert.match(publish({ argv: [...ARGS, '--dry-run'], run, readFile }), /^DRY-RUN: no publica/);
    assert.deepEqual(calls, [VIEW, `${PUBLISH} --dry-run`]);
  });

  it('npm publish falla: falla', () => {
    const { run, readFile } = setup(npmError('E404'), result(1, '', 'npm error code E403'));
    assert.throws(
      () => publish({ argv: ARGS, run, readFile }),
      /npm publish falló \(código 1\): npm error code E403/,
    );
  });
});

describe('release-publish · cualquier otra respuesta de npm falla sin publicar', () => {
  const answers = {
    'sin respuesta (error de red)': result(1, '', 'npm error code ETIMEDOUT'),
    'otro error de npm (E403)': npmError('E403'),
    'error de npm sin JSON': result(1, 'Bad Gateway'),
    'respuesta vacía': result(0, ''),
    'respuesta que no es una integridad': result(0, JSON.stringify({ integrity: SAME })),
    'integridad que no es sha512': result(0, JSON.stringify('sha1-abc')),
  };
  for (const [situation, view] of Object.entries(answers)) {
    it(situation, () => {
      const { run, calls, readFile } = setup(view);
      assert.throws(() => publish({ argv: ARGS, run, readFile }), /No se publica/);
      assert.deepEqual(calls, [VIEW]);
    });
  }
});

describe('release-publish · argumentos', () => {
  const invalid = {
    'otro paquete': [[TARBALL, 'otro', '0.6.0'], /Solo se publica liv-components/],
    'versión que no es X.Y.Z': [[TARBALL, 'liv-components', '0.6.0-next.1'], /no es X\.Y\.Z/],
    'archivo que no es .tgz': [
      ['paquete.zip', 'liv-components', '0.6.0'],
      /no es un archivo \.tgz/,
    ],
    'archivo que parece una opción': [
      ['--registry=x.tgz', 'liv-components', '0.6.0'],
      /no es un archivo \.tgz/,
    ],
    'faltan argumentos': [[TARBALL, 'liv-components'], /Uso:/],
    'sobran argumentos': [[...ARGS, '--tag', 'next'], /Uso:/],
  };
  for (const [situation, [argv, message]] of Object.entries(invalid)) {
    it(`falla con ${situation}, sin consultar npm`, () => {
      const { run, calls, readFile } = setup(npmError('E404'));
      assert.throws(() => publish({ argv, run, readFile }), message);
      assert.deepEqual(calls, []);
    });
  }

  it('falla si no puede leer el .tgz, sin consultar npm', () => {
    const { run, calls } = setup(npmError('E404'));
    assert.throws(() => publish({ argv: ARGS, run, readFile: fakeFiles({}) }), /No se pudo leer/);
    assert.deepEqual(calls, []);
  });

  it('calcula la integridad como npm (sha512 en base64)', () => {
    assert.equal(
      integrityOf(Buffer.from('')),
      'sha512-z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg/SpIdNs6c5H0NE8XYXysP+DGNKHfuwvY7kxvUdBeoGlODJ6+SfaPg==',
    );
  });
});

describe('release-publish · línea de comandos', () => {
  it('termina en 0 al publicar y en 1, con la anotación de error, al fallar', () => {
    const logs = [];
    const errors = [];
    const io = {
      env: { GITHUB_ACTIONS: 'true' },
      log: (l) => logs.push(l),
      error: (l) => errors.push(l),
    };
    assert.equal(main({ argv: ARGS, ...setup(npmError('E404')), ...io }), 0);
    assert.equal(main({ argv: ARGS, ...setup(result(0, JSON.stringify(OTHER))), ...io }), 1);
    assert.match(errors[0], /^::error::.*otra integridad/);
  });

  it('no importa ningún archivo propio: viaja solo como artefacto de `decidir`', () => {
    const source = readFileSync(
      fileURLToPath(new URL('./release-publish.mjs', import.meta.url)),
      'utf8',
    );
    const imports = [...source.matchAll(/^import .* from '([^']+)';$/gm)].map((match) => match[1]);
    assert.ok(imports.length > 0);
    assert.ok(
      imports.every((name) => name.startsWith('node:')),
      imports.join(', '),
    );
  });
});
