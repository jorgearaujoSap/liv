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
const PUBLISHED = ['0.0.0-stage', '0.1.0', '0.2.0', '0.3.0', '0.3.1', '0.4.0', '0.5.0'];

/** A main commit; each option overrides one fact of the default (this commit bumps 0.5.0 → 0.6.0). */
function scenario({
  version = '0.6.0',
  name = 'liv-components',
  parentVersion = '0.5.0',
  parentTree = result(0, `100644 blob ${'c'.repeat(40)}\t${LIB}\n`),
  parentShow,
  hasParent = true,
  nvmrc = '22.23.5\n',
  packageManager = 'npm@11.12.0',
  npmView = result(
    0,
    JSON.stringify({ versions: PUBLISHED, 'dist-tags': { latest: '0.5.0' } }, null, 2),
  ),
  gitHead = result(0, ''),
  tags = result(0, ''),
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
    [`git ls-tree HEAD^1 -- ${LIB}`]: parentTree,
    [`git show HEAD^1:./${LIB}`]:
      parentShow ?? result(0, JSON.stringify({ name: 'liv-components', version: parentVersion })),
    [`npm view ${name} versions dist-tags --json --prefer-online`]: npmView,
    [`npm view ${name}@${version} gitHead --json --prefer-online`]: gitHead,
    [`git ls-remote --tags origin refs/tags/ui-v${version} refs/tags/ui-v${version}^{}`]: tags,
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
    assert.equal(decision.node, '22.23.5');
    assert.equal(decision.npm, '11.12.0');
  });

  it('publicar: la etiqueta ya está en este mismo commit y la versión no se publicó', () => {
    const decision = decide(scenario({ tags: tagAt('0.6.0', HEAD) }));
    assert.equal(decision.salida, 'publicar');
  });

  it('publicar: el commit padre no tenía el package.json de la librería', () => {
    const decision = decide(scenario({ parentTree: result(0, '') }));
    assert.equal(decision.salida, 'publicar');
  });

  it('publicar: el commit padre tenía una versión previa (no X.Y.Z)', () => {
    const decision = decide(scenario({ parentVersion: '0.6.0-next.1' }));
    assert.equal(decision.salida, 'publicar');
  });

  it('nada con aviso: versión no publicada que no subió este commit', () => {
    const decision = decide(scenario({ parentVersion: '0.6.0' }));
    assert.equal(decision.salida, 'nada');
    assert.match(
      decision.mensaje,
      /0\.6\.0 está pendiente en la ejecución del commit que la subió; solo ese commit la publica/,
    );
  });

  it('nada con aviso: no publicada, no la subió este commit y la etiqueta está en otro', () => {
    const decision = decide(scenario({ parentVersion: '0.6.0', tags: tagAt('0.6.0', OTHER) }));
    assert.equal(decision.salida, 'nada');
    assert.match(decision.mensaje, /pendiente/);
  });

  it('etiquetar: ya publicada, este commit subió la versión y falta la etiqueta', () => {
    const decision = decide(scenario({ version: '0.5.0', parentVersion: '0.4.0' }));
    assert.equal(decision.salida, 'etiquetar');
    assert.equal(decision.commit, HEAD);
  });

  it('etiquetar: npm registra que se publicó desde este mismo commit (gitHead)', () => {
    const decision = decide(
      scenario({ version: '0.5.0', parentVersion: '0.4.0', gitHead: result(0, `"${HEAD}"\n`) }),
    );
    assert.equal(decision.salida, 'etiquetar');
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

  it('nada: ya publicada, no la subió este commit y sin etiqueta (no se etiqueta)', () => {
    const decision = decide(scenario({ version: '0.5.0', parentVersion: '0.5.0' }));
    assert.equal(decision.salida, 'nada');
  });

  it('nada: este commit bajó la versión a una publicada sin etiqueta (no la subió: un revert)', () => {
    const decision = decide(scenario({ version: '0.5.0', parentVersion: '0.6.0' }));
    assert.equal(decision.salida, 'nada');
  });

  it('nada: este commit bajó la versión a una no publicada mayor que la última (un revert)', () => {
    const decision = decide(scenario({ version: '0.6.0', parentVersion: '0.7.0' }));
    assert.equal(decision.salida, 'nada');
    assert.match(decision.mensaje, /no subió la versión/);
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

  it('error: npm registra que la versión se publicó desde otro commit (no se etiqueta este)', () => {
    const decision = decide(
      scenario({ version: '0.5.0', parentVersion: '0.4.0', gitHead: result(0, `"${OTHER}"\n`) }),
    );
    assert.equal(decision.salida, 'error');
    assert.match(decision.mensaje, /se publicó desde el commit b{40}, no desde este/);
  });

  it('error: npm no responde de qué commit se publicó la versión por etiquetar', () => {
    const decision = decide(
      scenario({ version: '0.5.0', parentVersion: '0.4.0', gitHead: result(1, '', 'ETIMEDOUT') }),
    );
    assert.equal(decision.salida, 'error');
    assert.match(decision.mensaje, /npm no respondió de qué commit/);
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
    for (const parentVersion of ['0.5.0', '0.6.0']) {
      it(`error: ${situation} (${parentVersion === '0.5.0' ? 'subió' : 'no subió'} la versión)`, () => {
        const decision = decide(scenario({ npmView, parentVersion }));
        assert.equal(decision.salida, 'error');
        assert.match(decision.mensaje, /npm no respondió/);
      });
    }
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
    'la versión tiene ceros a la izquierda': [
      { version: '0.06.0' },
      /La versión "0\.06\.0" no es X\.Y\.Z/,
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
    'packageManager lleva el hash de corepack': [
      { packageManager: 'npm@11.12.0+sha512.abc' },
      /packageManager/,
    ],
    'packageManager es anterior a npm 11.5.1': [
      { packageManager: 'npm@11.4.2' },
      /11\.5\.1 o mayor/,
    ],
    'no está el commit padre': [{ hasParent: false }, /commit padre/],
    'no se puede leer el árbol del padre': [
      { parentTree: result(128, '', 'fatal: bad object') },
      /árbol del commit padre/,
    ],
    'git show del padre falla': [
      { parentShow: result(128, '', 'fatal: bad object') },
      /versión del commit padre/,
    ],
    'el package.json del padre no es JSON': [
      { parentShow: result(0, 'no es JSON') },
      /versión del commit padre/,
    ],
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

  it('no consulta npm ni git si la versión no es válida', () => {
    const { run, readFile, calls } = scenario({ version: '1.0.0; rm -rf /' });
    assert.equal(decide({ run, readFile }).salida, 'error');
    assert.deepEqual(calls, []);
  });

  it('en ningún caso ejecuta npm publish, npm login ni git push o git tag', () => {
    const cases = [{}, { version: '0.5.0', parentVersion: '0.4.0' }, { parentVersion: '0.6.0' }];
    for (const options of cases) {
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
    assert.match(written, /^node=22\.23\.5$/m);
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

  it('un error de git en varias líneas no agrega salidas ni comandos del workflow', () => {
    const stderr = 'fatal: x\nsalida=publicar\n::add-mask::secreto\n%0A';
    const { written, errors } = runMain({ tags: result(128, '', stderr) });
    const lines = written.trim().split('\n');
    assert.deepEqual(
      lines.filter((line) => line.startsWith('salida=')),
      ['salida=error'],
    );
    assert.ok(
      lines.every((line) => /^(salida|mensaje)=/.test(line)),
      written,
    );
    assert.equal(errors.length, 1);
    assert.ok(!errors[0].includes('\n'));
    assert.ok(errors[0].includes('%250A'), errors[0]);
  });
});
