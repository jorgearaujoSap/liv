// Tests of the publish decision (feature 002, criteria 002-04, 002-05 and 002-06; spec.md, table of
// release-decide outputs). npm and git are simulated: no test reaches the registry or the remote.
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';

import { decide, main } from './release-decide.mjs';
import { fakeFiles, fakeRun, npmError, result } from './test-helpers.mjs';

const HEAD = 'a'.repeat(40);
const OTHER = 'b'.repeat(40);
const LIB = 'projects/liv-components/package.json';
const PARENT_LIB = `HEAD^1:./${LIB}`;
const PUBLISHED = ['0.0.0-stage', '0.1.0', '0.2.0', '0.3.0', '0.3.1', '0.4.0', '0.5.0'];

/** A main commit; each option overrides one fact of the default (this commit bumps 0.5.0 → 0.6.0). */
function scenario({
  version = '0.6.0',
  name = 'liv-components',
  parentVersion = '0.5.0',
  parentHasFile = true,
  hasParent = true,
  nvmrc = '22.21.1\n',
  packageManager = 'npm@11.12.0',
  npmView = result(
    0,
    JSON.stringify({ versions: PUBLISHED, 'dist-tags': { latest: '0.5.0' } }, null, 2),
  ),
  tags = result(0, ''),
  bumpLog = result(0, ''),
} = {}) {
  const files = fakeFiles({
    [LIB]: JSON.stringify({ name, version }),
    '.nvmrc': nvmrc,
    'package.json': JSON.stringify(packageManager ? { packageManager } : {}),
  });
  const { run, calls } = fakeRun({
    'git rev-parse HEAD': result(0, `${HEAD}\n`),
    'git rev-parse --verify --quiet HEAD^1^{commit}': hasParent
      ? result(0, `${OTHER}\n`)
      : result(1),
    [`git cat-file -e ${PARENT_LIB}`]: parentHasFile ? result(0) : result(128),
    [`git show ${PARENT_LIB}`]: result(
      0,
      JSON.stringify({ name: 'liv-components', version: parentVersion }),
    ),
    [`npm view ${name} versions dist-tags --json --prefer-online`]: npmView,
    [`git ls-remote --tags origin refs/tags/ui-v${version} refs/tags/ui-v${version}^{}`]: tags,
    [`git log -1 --format=%h -S"version": "${version}" -- ${LIB}`]: bumpLog,
  });
  return { run, readFile: files, calls };
}

function tagAt(version, commit) {
  return result(0, `${commit}\trefs/tags/ui-v${version}\n`);
}

describe('release-decide · salidas de la tabla del diseño técnico (002-05, 002-06)', () => {
  it('publicar: este commit subió la versión, mayor que la última y no publicada', () => {
    const decision = decide(scenario());
    assert.equal(decision.salida, 'publicar');
    assert.equal(decision.nombre, 'liv-components');
    assert.equal(decision.version, '0.6.0');
    assert.equal(decision.commit, HEAD);
    assert.equal(decision.node, '22.21.1');
    assert.equal(decision.npm, '11.12.0');
  });

  it('publicar: la etiqueta ya está en este mismo commit y la versión no se publicó', () => {
    const decision = decide(scenario({ tags: tagAt('0.6.0', HEAD) }));
    assert.equal(decision.salida, 'publicar');
  });

  it('publicar: el commit padre no tenía el package.json de la librería', () => {
    const decision = decide(scenario({ parentHasFile: false }));
    assert.equal(decision.salida, 'publicar');
  });

  it('nada con aviso: versión no publicada que no subió este commit', () => {
    const decision = decide(scenario({ parentVersion: '0.6.0' }));
    assert.equal(decision.salida, 'nada');
    assert.match(
      decision.mensaje,
      /0\.6\.0 está pendiente en la ejecución del commit que la subió/,
    );
  });

  it('nada con aviso: nombra el commit que subió la versión cuando el historial lo tiene', () => {
    const decision = decide(scenario({ parentVersion: '0.6.0', bumpLog: result(0, 'abc1234\n') }));
    assert.equal(decision.salida, 'nada');
    assert.match(decision.mensaje, /pendiente en la ejecución del commit abc1234/);
  });

  it('etiquetar: ya publicada, este commit subió la versión y falta la etiqueta', () => {
    const decision = decide(scenario({ version: '0.5.0', parentVersion: '0.4.0' }));
    assert.equal(decision.salida, 'etiquetar');
    assert.equal(decision.commit, HEAD);
  });

  it('nada: ya publicada, este commit la subió y la etiqueta está en este commit', () => {
    const decision = decide(
      scenario({ version: '0.5.0', parentVersion: '0.4.0', tags: tagAt('0.5.0', HEAD) }),
    );
    assert.equal(decision.salida, 'nada');
  });

  it('nada: ya publicada y este commit no subió la versión (cambio de ui/ sin versión)', () => {
    const decision = decide(
      scenario({ version: '0.5.0', parentVersion: '0.5.0', tags: tagAt('0.5.0', OTHER) }),
    );
    assert.equal(decision.salida, 'nada');
    assert.match(decision.mensaje, /ya está publicada/);
  });

  it('error: menor que la última y no publicada (la subió este commit)', () => {
    const decision = decide(scenario({ version: '0.4.5', parentVersion: '0.4.0' }));
    assert.equal(decision.salida, 'error');
    assert.match(decision.mensaje, /0\.4\.5 es menor que la última publicada \(0\.5\.0\)/);
  });

  it('error: menor que la última y no publicada (no la subió este commit)', () => {
    const decision = decide(scenario({ version: '0.4.5', parentVersion: '0.4.5' }));
    assert.equal(decision.salida, 'error');
    assert.match(decision.mensaje, /menor que la última publicada/);
  });

  it('error: la etiqueta existe en otro commit (versión por publicar)', () => {
    const decision = decide(scenario({ tags: tagAt('0.6.0', OTHER) }));
    assert.equal(decision.salida, 'error');
    assert.match(decision.mensaje, /ui-v0\.6\.0 ya existe en el commit b{40}/);
  });

  it('error: la etiqueta existe en otro commit (versión publicada por subir la etiqueta)', () => {
    const decision = decide(
      scenario({ version: '0.5.0', parentVersion: '0.4.0', tags: tagAt('0.5.0', OTHER) }),
    );
    assert.equal(decision.salida, 'error');
    assert.match(decision.mensaje, /ui-v0\.5\.0 ya existe en el commit b{40}/);
  });

  it('error: una etiqueta anotada en otro commit se compara por el commit al que apunta', () => {
    const annotated = result(0, `${HEAD}\trefs/tags/ui-v0.6.0\n${OTHER}\trefs/tags/ui-v0.6.0^{}\n`);
    const decision = decide(scenario({ tags: annotated }));
    assert.equal(decision.salida, 'error');
    assert.match(decision.mensaje, /ya existe en el commit b{40}/);
  });

  it('publicar: una etiqueta anotada que apunta a este commit no es ajena', () => {
    const annotated = result(0, `${OTHER}\trefs/tags/ui-v0.6.0\n${HEAD}\trefs/tags/ui-v0.6.0^{}\n`);
    assert.equal(decide(scenario({ tags: annotated })).salida, 'publicar');
  });
});

describe('release-decide · npm sin respuesta nunca es «nada que publicar» (002-05)', () => {
  const failures = {
    'npm responde con un error de red': result(1, '', 'npm error code ETIMEDOUT'),
    'npm responde E404 por el paquete': npmError('E404'),
    'npm responde vacío': result(0, ''),
    'npm responde algo que no es JSON': result(0, 'Bad Gateway'),
    'npm responde sin la lista de versiones': result(0, JSON.stringify({ 'dist-tags': {} })),
    'npm responde con una lista de versiones vacía': result(
      0,
      JSON.stringify({ versions: [], 'dist-tags': {} }),
    ),
  };
  for (const [situation, npmView] of Object.entries(failures)) {
    it(`error: ${situation}`, () => {
      const decision = decide(scenario({ npmView }));
      assert.equal(decision.salida, 'error');
      assert.match(decision.mensaje, /npm no respondió/);
    });
  }

  it('acepta una sola versión publicada, que npm devuelve como texto y no como lista', () => {
    const npmView = result(
      0,
      JSON.stringify({ versions: '0.5.0', 'dist-tags': { latest: '0.5.0' } }),
    );
    assert.equal(decide(scenario({ npmView })).salida, 'publicar');
  });
});

describe('release-decide · validaciones previas (002-04)', () => {
  const invalid = {
    'la versión no es X.Y.Z': [
      { version: '0.6.0-next.1' },
      /La versión "0\.6\.0-next\.1" no es X\.Y\.Z/,
    ],
    'el paquete no es liv-components': [
      { name: 'otro-paquete' },
      /El paquete se llama "otro-paquete"/,
    ],
    '.nvmrc no tiene una versión exacta': [{ nvmrc: '22\n' }, /ui\/\.nvmrc dice "22"/],
    '.nvmrc es anterior a Node 22.14.0': [{ nvmrc: '22.13.1\n' }, /22\.14\.0 o mayor/],
    'packageManager no existe': [{ packageManager: null }, /packageManager/],
    'packageManager no es una versión exacta de npm': [
      { packageManager: 'npm@^11.5.1' },
      /packageManager/,
    ],
    'packageManager es anterior a npm 11.5.1': [
      { packageManager: 'npm@11.4.2' },
      /11\.5\.1 o mayor/,
    ],
    'no está el commit padre': [{ hasParent: false }, /commit padre/],
    'git ls-remote falla': [
      { tags: result(128, '', 'fatal: could not read') },
      /etiquetas remotas/,
    ],
  };
  for (const [situation, [options, message]] of Object.entries(invalid)) {
    it(`error: ${situation}`, () => {
      const decision = decide(scenario(options));
      assert.equal(decision.salida, 'error');
      assert.match(decision.mensaje, message);
    });
  }

  it('no consulta npm ni git si la versión no es válida, y nunca publica ni etiqueta', () => {
    const { run, readFile, calls } = scenario({ version: '1.0.0; rm -rf /' });
    assert.equal(decide({ run, readFile }).salida, 'error');
    assert.ok(!calls.some((call) => call.startsWith('npm')), calls.join('\n'));
  });

  it('en ningún caso ejecuta npm publish, npm login ni git push o git tag', () => {
    for (const options of [
      {},
      { version: '0.5.0', parentVersion: '0.4.0' },
      { parentVersion: '0.6.0' },
    ]) {
      const { run, readFile, calls } = scenario(options);
      decide({ run, readFile });
      assert.ok(
        !calls.some((call) => /npm (publish|login|adduser)|git (push|tag)/.test(call)),
        calls.join('\n'),
      );
    }
  });
});

describe('release-decide · salida para el workflow', () => {
  function runMain(options) {
    const dir = mkdtempSync(join(tmpdir(), 'release-decide-'));
    const output = join(dir, 'output');
    const logs = [];
    const errors = [];
    const code = main({
      ...scenario(options),
      env: { GITHUB_OUTPUT: output, GITHUB_ACTIONS: 'true' },
      log: (line) => logs.push(line),
      error: (line) => errors.push(line),
    });
    let written = '';
    try {
      written = readFileSync(output, 'utf8');
    } catch {
      written = '';
    }
    return { code, written, logs, errors };
  }

  it('publicar termina en 0 y escribe la salida, el nombre, la versión, el commit, Node y npm', () => {
    const { code, written } = runMain();
    assert.equal(code, 0);
    assert.match(written, /^salida=publicar$/m);
    assert.match(written, /^nombre=liv-components$/m);
    assert.match(written, /^version=0\.6\.0$/m);
    assert.match(written, new RegExp(`^commit=${HEAD}$`, 'm'));
    assert.match(written, /^node=22\.21\.1$/m);
    assert.match(written, /^npm=11\.12\.0$/m);
  });

  it('nada termina en 0 con su mensaje', () => {
    const { code, written, logs } = runMain({ version: '0.5.0', parentVersion: '0.5.0' });
    assert.equal(code, 0);
    assert.match(written, /^salida=nada$/m);
    assert.ok(
      logs.some((line) => /Nada que publicar/.test(line)),
      logs.join('\n'),
    );
  });

  it('error termina en 1, escribe salida=error y anota el error para GitHub Actions', () => {
    const { code, written, errors } = runMain({ npmView: result(1, '') });
    assert.equal(code, 1);
    assert.match(written, /^salida=error$/m);
    assert.ok(
      errors.some((line) => line.startsWith('::error::')),
      errors.join('\n'),
    );
  });

  it('un mensaje con saltos de línea no puede inyectar otras salidas', () => {
    const { written } = runMain({ version: '0.6.0\nsalida=publicar' });
    const lines = written.trim().split('\n');
    assert.deepEqual(
      lines.filter((line) => line.startsWith('salida=')),
      ['salida=error'],
    );
  });
});
