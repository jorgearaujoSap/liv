// Tests of the idempotent publish (feature 002, 002-05, 002-06 and 002-08). npm is simulated: no test
// reaches the registry, and the only publish a test can run is the simulated one.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

import { integrityOf, main, publish, readPackedManifest } from './release-publish.mjs';
import { fakeFiles, fakeRun, makeTarball, npmError, result } from './test-helpers.mjs';

const TARBALL = 'dist/liv-components-0.6.0.tgz';
const CONTENT = makeTarball({
  'package/README.md': '# liv-components',
  'package/package.json': JSON.stringify({ name: 'liv-components', version: '0.6.0' }),
});
const SAME = integrityOf(CONTENT);
const OTHER = integrityOf(Buffer.from('otro contenido'));
const VIEW = 'npm view liv-components@0.6.0 dist.integrity --json --prefer-online';
const VERSIONS = 'npm view liv-components versions --json --prefer-online';
const PUBLISH = `npm publish ${TARBALL} --ignore-scripts --access public`;
const ARGS = [TARBALL, 'liv-components', '0.6.0'];
const LISTED = result(0, JSON.stringify(['0.0.0-stage', '0.4.0', '0.5.0']));

function setup({
  view,
  versions = LISTED,
  publishResult = result(0, '+ liv-components@0.6.0'),
  content = CONTENT,
} = {}) {
  const { run, calls } = fakeRun({
    [VIEW]: view,
    [VERSIONS]: versions,
    [PUBLISH]: publishResult,
    [`${PUBLISH} --dry-run`]: publishResult,
  });
  return { run, calls, readFile: fakeFiles({ [TARBALL]: content }) };
}

describe('release-publish · no publica dos veces (002-06)', () => {
  it('la versión ya existe con la misma integridad: sigue sin publicar', () => {
    const { run, calls, readFile } = setup({ view: result(0, JSON.stringify(SAME)) });
    assert.match(
      publish({ argv: ARGS, run, readFile }),
      /^liv-components@0\.6\.0 ya está publicada con la misma integridad: no se publica otra vez/,
    );
    assert.deepEqual(calls, [VIEW]);
  });

  it('la versión ya existe con otra integridad: falla sin publicar', () => {
    const { run, calls, readFile } = setup({ view: result(0, JSON.stringify(OTHER)) });
    assert.throws(
      () => publish({ argv: ARGS, run, readFile }),
      /ya está publicada con otra integridad/,
    );
    assert.deepEqual(calls, [VIEW]);
  });

  it('npm responde E404: publica el .tgz con --ignore-scripts y --access public', () => {
    const { run, calls, readFile } = setup({
      view: npmError('E404', 'No match found for version'),
    });
    assert.match(publish({ argv: ARGS, run, readFile }), /^Publicada liv-components@0\.6\.0/);
    assert.deepEqual(calls, [VIEW, VERSIONS, PUBLISH]);
  });

  it('con --dry-run, solo ejecuta npm publish --dry-run y lo dice', () => {
    const { run, calls, readFile } = setup({ view: npmError('E404') });
    assert.match(
      publish({ argv: [...ARGS, '--dry-run'], run, readFile }),
      /^DRY-RUN: no publica\./,
    );
    assert.deepEqual(calls, [VIEW, VERSIONS, `${PUBLISH} --dry-run`]);
  });

  it('con --dry-run y la versión ya publicada con la misma integridad, también lo dice', () => {
    const { run, readFile } = setup({ view: result(0, JSON.stringify(SAME)) });
    assert.match(
      publish({ argv: [...ARGS, '--dry-run'], run, readFile }),
      /^DRY-RUN: no publica\. .*misma integridad/,
    );
  });

  it('npm publish falla: falla', () => {
    const { run, readFile } = setup({
      view: npmError('E404'),
      publishResult: result(1, '', 'npm error code E403\nnpm error otra línea'),
    });
    assert.throws(
      () => publish({ argv: ARGS, run, readFile }),
      /npm publish falló \(npm error code E403 npm error otra línea\)/,
    );
  });
});

describe('release-publish · antes de publicar, la versión debe ser mayor que la última (002-05)', () => {
  const blocked = {
    'menor que la última publicada (re-ejecución de un publicar antiguo)': [
      result(0, JSON.stringify(['0.5.0', '0.7.0'])),
      /no es mayor que la última publicada \(0\.7\.0\)/,
    ],
    'npm no responde con las versiones': [
      result(1, '', 'ETIMEDOUT'),
      /npm no respondió con las versiones/,
    ],
    'npm responde E404 por el paquete': [npmError('E404'), /npm no respondió con las versiones/],
    'npm responde una lista vacía': [result(0, '[]'), /npm no respondió con las versiones/],
    'npm lista la versión aunque dijo E404': [
      result(0, JSON.stringify(['0.5.0', '0.6.0'])),
      /lista liv-components@0\.6\.0 como publicada/,
    ],
  };
  for (const [situation, [versions, message]] of Object.entries(blocked)) {
    it(`no publica: ${situation}`, () => {
      const { run, calls, readFile } = setup({ view: npmError('E404'), versions });
      assert.throws(() => publish({ argv: ARGS, run, readFile }), message);
      assert.deepEqual(calls, [VIEW, VERSIONS]);
    });
  }
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
      const { run, calls, readFile } = setup({ view });
      assert.throws(() => publish({ argv: ARGS, run, readFile }), /No se publica/);
      assert.deepEqual(calls, [VIEW]);
    });
  }
});

describe('release-publish · el .tgz es el que se pide publicar (002-08)', () => {
  const tarballs = {
    'el .tgz trae otra versión': [
      makeTarball({
        'package/package.json': JSON.stringify({ name: 'liv-components', version: '0.7.0' }),
      }),
      /El \.tgz es liv-components@0\.7\.0 y se pidió publicar liv-components@0\.6\.0/,
    ],
    'el .tgz trae otro paquete': [
      makeTarball({ 'package/package.json': JSON.stringify({ name: 'otro', version: '0.6.0' }) }),
      /El \.tgz es otro@0\.6\.0/,
    ],
    'el .tgz no tiene package.json': [
      makeTarball({ 'package/README.md': 'x' }),
      /0 archivos package\/package\.json/,
    ],
    'el .tgz tiene dos package.json': [
      makeTarball([
        ['package/package.json', JSON.stringify({ name: 'liv-components', version: '0.6.0' })],
        ['package/package.json', JSON.stringify({ name: 'liv-components', version: '0.7.0' })],
      ]),
      /2 archivos package\/package\.json, no uno/,
    ],
    'el archivo no es un .tgz': [Buffer.from('no es gzip'), /No se pudo leer/],
  };
  for (const [situation, [content, message]] of Object.entries(tarballs)) {
    it(`falla sin consultar npm: ${situation}`, () => {
      const { run, calls, readFile } = setup({ view: npmError('E404'), content });
      assert.throws(() => publish({ argv: ARGS, run, readFile }), message);
      assert.deepEqual(calls, []);
    });
  }

  it('lee el package.json de un .tgz real de npm pack', () => {
    const dir = mkdtempSync(join(tmpdir(), 'release-publish-pack-'));
    writeFileSync(
      join(dir, 'package.json'),
      JSON.stringify({ name: 'liv-components', version: '0.6.0', repository: { type: 'git' } }),
    );
    execFileSync('npm', ['pack', '--pack-destination', dir, '--ignore-scripts'], {
      cwd: dir,
      stdio: 'pipe',
      shell: process.platform === 'win32',
    });
    const tgz = readdirSync(dir).find((file) => file.endsWith('.tgz'));
    const manifest = readPackedManifest(readFileSync(join(dir, tgz)));
    assert.equal(manifest.name, 'liv-components');
    assert.equal(manifest.version, '0.6.0');
    assert.deepEqual(manifest.repository, { type: 'git' });
  });
});

describe('release-publish · argumentos', () => {
  const invalid = {
    'otro paquete': [[TARBALL, 'otro', '0.6.0'], /Solo se publica liv-components/],
    'versión que no es X.Y.Z': [[TARBALL, 'liv-components', '0.6.0-next.1'], /no es X\.Y\.Z/],
    'versión con ceros a la izquierda': [[TARBALL, 'liv-components', '0.06.0'], /no es X\.Y\.Z/],
    'archivo que no es .tgz': [
      ['paquete.zip', 'liv-components', '0.6.0'],
      /no es la ruta de un archivo \.tgz/,
    ],
    'archivo que parece una opción': [
      ['--registry=x.tgz', 'liv-components', '0.6.0'],
      /no es la ruta/,
    ],
    'ruta con sintaxis de shell': [['a&calc.tgz', 'liv-components', '0.6.0'], /no es la ruta/],
    'faltan argumentos': [[TARBALL, 'liv-components'], /Uso:/],
    'sobran argumentos': [[...ARGS, '--tag', 'next'], /Uso:/],
  };
  for (const [situation, [argv, message]] of Object.entries(invalid)) {
    it(`falla con ${situation}, sin consultar npm`, () => {
      const { run, calls, readFile } = setup({ view: npmError('E404') });
      assert.throws(() => publish({ argv, run, readFile }), message);
      assert.deepEqual(calls, []);
    });
  }

  it('falla si no puede leer el .tgz, sin consultar npm', () => {
    const { run, calls } = setup({ view: npmError('E404') });
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
  it('termina en 0 al publicar y en 1, con la anotación de error en una línea, al fallar', () => {
    const logs = [];
    const errors = [];
    const io = {
      env: { GITHUB_ACTIONS: 'true' },
      log: (line) => logs.push(line),
      error: (line) => errors.push(line),
    };
    assert.equal(main({ argv: ARGS, ...setup({ view: npmError('E404') }), ...io }), 0);
    const failing = setup({ view: result(1, '', 'x\n::add-mask::y\n%0A') });
    assert.equal(main({ argv: ARGS, ...failing, ...io }), 1);
    assert.equal(errors.length, 1);
    assert.match(errors[0], /^::error::npm no respondió/);
    assert.ok(!errors[0].includes('\n') && errors[0].includes('%250A'), errors[0]);
  });

  it('funciona solo, copiado a un directorio sin los demás scripts (artefacto de `decidir`)', () => {
    const dir = mkdtempSync(join(tmpdir(), 'release-publish-solo-'));
    const copy = join(dir, 'release-publish.mjs');
    copyFileSync(fileURLToPath(new URL('./release-publish.mjs', import.meta.url)), copy);
    let failure;
    try {
      execFileSync(process.execPath, [copy], { cwd: dir, stdio: 'pipe', encoding: 'utf8' });
    } catch (error) {
      failure = error;
    }
    assert.equal(failure?.status, 1);
    assert.match(failure.stderr, /Uso: node release-publish\.mjs/);
    assert.doesNotMatch(failure.stderr, /ERR_MODULE_NOT_FOUND/);
  });
});
