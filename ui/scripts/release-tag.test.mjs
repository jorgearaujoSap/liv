// Tests of the tag and the release (feature 002, 002-03 and 002-06). `gh` is simulated: no test reaches GitHub,
// and the only tag or release a test can create is the simulated one.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdtempSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

import { main, parseArgs, tag } from './release-tag.mjs';
import { fakeFiles, fakeRun, result } from './test-helpers.mjs';

const COMMIT = 'a'.repeat(40);
const OTHER_COMMIT = 'b'.repeat(40);
const ANNOTATED = 'c'.repeat(40);
const NOTES = 'notas.md';
const ARGS = ['liv-components', '0.6.0', COMMIT, NOTES];
const ENV = { GITHUB_REPOSITORY: 'jorgearaujoSap/liv' };
const REF = 'gh api repos/jorgearaujoSap/liv/git/ref/tags/ui-v0.6.0';
const CREATE_REF =
  'gh api --method POST repos/jorgearaujoSap/liv/git/refs -f ref=refs/tags/ui-v0.6.0 ' +
  `-f sha=${COMMIT}`;
const RELEASE = 'gh api repos/jorgearaujoSap/liv/releases/tags/ui-v0.6.0';
const CREATE_RELEASE =
  `gh release create ui-v0.6.0 --repo jorgearaujoSap/liv --verify-tag --latest=false --notes-file ${NOTES} ` +
  '--title liv-components 0.6.0';
const NOT_FOUND = result(1, '{"message":"Not Found"}', 'gh: Not Found (HTTP 404)');
const SERVER_ERROR = result(1, '', 'gh: Server Error (HTTP 500)');

const commitRef = (sha) => result(0, JSON.stringify({ object: { type: 'commit', sha } }));
const NOTES_TEXT = '### Añadido\n\n- Un componente.\n';
const readFile = fakeFiles({ [NOTES]: NOTES_TEXT });
const release = (body = NOTES_TEXT) => result(0, JSON.stringify({ tag_name: 'ui-v0.6.0', body }));

function setup(responses) {
  const { run, calls } = fakeRun(responses);
  return { run, calls };
}

function runTag(responses, argv = ARGS) {
  const { run, calls } = setup(responses);
  const messages = tag({ argv, env: ENV, run, readFile });
  return { messages, calls };
}

describe('release-tag · crea la etiqueta en el commit de esta ejecución (002-03)', () => {
  it('no existe: crea la etiqueta por la API en ese commit y después la release', () => {
    const { messages, calls } = runTag({
      [REF]: NOT_FOUND,
      [CREATE_REF]: result(0, '{}'),
      [RELEASE]: NOT_FOUND,
      [CREATE_RELEASE]: result(0, 'https://github.com/jorgearaujoSap/liv/releases/tag/ui-v0.6.0'),
    });
    assert.deepEqual(calls, [REF, CREATE_REF, RELEASE, CREATE_RELEASE]);
    assert.match(messages[0], /^Etiqueta ui-v0\.6\.0 creada en a{40}\./);
    assert.match(messages[1], /^Release ui-v0\.6\.0 creada\./);
  });

  it('existe en este mismo commit: sigue sin crearla y crea la release que falte', () => {
    const { messages, calls } = runTag({
      [REF]: commitRef(COMMIT),
      [RELEASE]: NOT_FOUND,
      [CREATE_RELEASE]: result(0),
    });
    assert.deepEqual(calls, [REF, RELEASE, CREATE_RELEASE]);
    assert.match(messages[0], /ya existe en el commit de esta ejecución/);
  });

  it('existe en este commit y la release también: no crea nada (volver a ejecutar todo)', () => {
    const { messages, calls } = runTag({ [REF]: commitRef(COMMIT), [RELEASE]: release() });
    assert.deepEqual(calls, [REF, RELEASE]);
    assert.match(messages[1], /^La release ui-v0\.6\.0 ya existe/);
  });

  it('la release ya existe con otro texto: no la cambia y lo dice', () => {
    const { messages, calls } = runTag({
      [REF]: commitRef(COMMIT),
      [RELEASE]: release('otro texto'),
    });
    assert.deepEqual(calls, [REF, RELEASE]);
    assert.match(messages[1], /ya existe, con un texto distinto.* No se cambia/);
  });

  it('existe en otro commit: falla y no la mueve ni crea la release', () => {
    const { run, calls } = setup({ [REF]: commitRef(OTHER_COMMIT) });
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile }),
      new RegExp(
        `ya existe en el commit ${OTHER_COMMIT}, no en el de esta ejecución.* No se mueve`,
      ),
    );
    assert.deepEqual(calls, [REF]);
  });

  it('es anotada y apunta a otro commit: falla', () => {
    const { run, calls } = setup({
      [REF]: result(0, JSON.stringify({ object: { type: 'tag', sha: ANNOTATED } })),
      [`gh api repos/jorgearaujoSap/liv/git/tags/${ANNOTATED}`]: result(
        0,
        JSON.stringify({ object: { type: 'commit', sha: OTHER_COMMIT } }),
      ),
    });
    assert.throws(() => tag({ argv: ARGS, env: ENV, run, readFile }), /No se mueve/);
    assert.equal(calls.length, 2);
  });

  it('es anotada y apunta a este commit: sigue', () => {
    const { calls } = runTag({
      [REF]: result(0, JSON.stringify({ object: { type: 'tag', sha: ANNOTATED } })),
      [`gh api repos/jorgearaujoSap/liv/git/tags/${ANNOTATED}`]: result(
        0,
        JSON.stringify({ object: { type: 'commit', sha: COMMIT } }),
      ),
      [RELEASE]: release(),
    });
    assert.equal(calls.length, 3);
  });

  it('una etiqueta que apunta a algo que no es un commit ni una etiqueta: falla', () => {
    const { run } = setup({
      [REF]: result(0, JSON.stringify({ object: { type: 'tree', sha: COMMIT } })),
    });
    assert.throws(() => tag({ argv: ARGS, env: ENV, run, readFile }), /no apunta a un commit/);
  });
});

describe('release-tag · carrera entre dos ejecuciones', () => {
  it('la otra creó la etiqueta entre la lectura y la creación, en este commit: sigue', () => {
    let reads = 0;
    const { messages } = runTag({
      [REF]: () => (reads++ === 0 ? NOT_FOUND : commitRef(COMMIT)),
      [CREATE_REF]: result(1, '', 'gh: Reference already exists (HTTP 422)'),
      [RELEASE]: release(),
    });
    assert.match(messages[0], /ya existe en el commit de esta ejecución/);
  });

  it('la otra la creó en otro commit: falla', () => {
    let reads = 0;
    const { run } = setup({
      [REF]: () => (reads++ === 0 ? NOT_FOUND : commitRef(OTHER_COMMIT)),
      [CREATE_REF]: result(1, '', 'gh: Reference already exists (HTTP 422)'),
    });
    assert.throws(() => tag({ argv: ARGS, env: ENV, run, readFile }), /No se mueve/);
  });

  it('la creación falla y la etiqueta sigue sin existir: falla con el motivo', () => {
    const { run } = setup({
      [REF]: NOT_FOUND,
      [CREATE_REF]: result(1, '', 'gh: Resource not accessible by integration (HTTP 403)'),
    });
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile }),
      /No se pudo crear la etiqueta ui-v0\.6\.0 \(gh: Resource not accessible by integration/,
    );
  });

  it('la otra creó la release entre la lectura y la creación: sigue', () => {
    let reads = 0;
    const { messages } = runTag({
      [REF]: commitRef(COMMIT),
      [RELEASE]: () => (reads++ === 0 ? NOT_FOUND : release()),
      [CREATE_RELEASE]: result(1, '', 'HTTP 422: Validation Failed'),
    });
    assert.match(messages[1], /ya existe/);
  });

  it('la release no se crea y sigue sin existir: falla con el motivo', () => {
    const { run } = setup({
      [REF]: commitRef(COMMIT),
      [RELEASE]: NOT_FOUND,
      [CREATE_RELEASE]: result(1, '', 'tag ui-v0.6.0 does not exist'),
    });
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile }),
      /No se pudo crear la release ui-v0\.6\.0 \(tag ui-v0\.6\.0 does not exist\)/,
    );
  });
});

describe('release-tag · una respuesta que no es «no existe» nunca se toma por «no existe»', () => {
  it('GitHub responde un error de servidor al leer la etiqueta: falla sin crear nada', () => {
    const { run, calls } = setup({ [REF]: SERVER_ERROR });
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile }),
      /GitHub no respondió si existe la etiqueta/,
    );
    assert.deepEqual(calls, [REF]);
  });

  it('GitHub responde algo ilegible: falla', () => {
    const { run } = setup({ [REF]: result(0, 'no es json') });
    assert.throws(() => tag({ argv: ARGS, env: ENV, run, readFile }), /ilegible/);
  });

  it('GitHub responde null o algo que no es un objeto: falla con un mensaje, no con una traza', () => {
    for (const body of ['null', '[]', '"texto"']) {
      const { run } = setup({ [REF]: result(0, body) });
      assert.throws(() => tag({ argv: ARGS, env: ENV, run, readFile }), /ilegible/);
    }
  });

  it('GitHub responde un error de servidor al leer la release: falla sin crearla', () => {
    const { run, calls } = setup({ [REF]: commitRef(COMMIT), [RELEASE]: SERVER_ERROR });
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile }),
      /GitHub no respondió si existe la release/,
    );
    assert.deepEqual(calls, [REF, RELEASE]);
  });

  it('no se puede leer una etiqueta anotada: falla', () => {
    const { run } = setup({
      [REF]: result(0, JSON.stringify({ object: { type: 'tag', sha: ANNOTATED } })),
      [`gh api repos/jorgearaujoSap/liv/git/tags/${ANNOTATED}`]: SERVER_ERROR,
    });
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile }),
      /No se pudo leer la etiqueta/,
    );
  });
});

describe('release-tag · argumentos y notas', () => {
  const bad =
    (argv, env = ENV) =>
    () =>
      parseArgs(argv, env);

  it('acepta los cuatro argumentos y el repositorio', () => {
    assert.deepEqual(parseArgs(ARGS, ENV), {
      name: 'liv-components',
      version: '0.6.0',
      commit: COMMIT,
      repository: 'jorgearaujoSap/liv',
      notes: NOTES,
      tag: 'ui-v0.6.0',
    });
  });

  it('rechaza lo que no es la librería, la versión, el commit, el repositorio o la ruta', () => {
    assert.throws(bad(['otro', '0.6.0', COMMIT, NOTES]), /Solo se etiqueta liv-components/);
    assert.throws(bad(['liv-components', '0.6', COMMIT, NOTES]), /no es X\.Y\.Z/);
    assert.throws(bad(['liv-components', '0.6.0-next.1', COMMIT, NOTES]), /no es X\.Y\.Z/);
    assert.throws(bad(['liv-components', '0.6.0', 'main', NOTES]), /no es un commit/);
    assert.throws(bad(['liv-components', '0.6.0', COMMIT.toUpperCase(), NOTES]), /no es un commit/);
    assert.throws(bad(ARGS, {}), /GITHUB_REPOSITORY/);
    assert.throws(bad(ARGS, { GITHUB_REPOSITORY: 'uno/dos/tres' }), /GITHUB_REPOSITORY/);
    assert.throws(bad(ARGS, { GITHUB_REPOSITORY: '../..' }), /GITHUB_REPOSITORY/);
    assert.throws(bad(ARGS, { GITHUB_REPOSITORY: 'dueno/..' }), /GITHUB_REPOSITORY/);
    assert.throws(bad(['liv-components', '0.6.0', COMMIT, '--title']), /no es la ruta/);
    assert.throws(bad(['liv-components', '0.6.0', COMMIT, 'a; rm -rf']), /no es la ruta/);
    assert.throws(bad(ARGS.slice(0, 3)), /Uso:/);
  });

  it('falla si no puede leer las notas o están vacías, antes de llamar a GitHub', () => {
    const { run, calls } = setup({});
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile: fakeFiles({}) }),
      /No se pudo leer notas\.md/,
    );
    assert.throws(
      () => tag({ argv: ARGS, env: ENV, run, readFile: fakeFiles({ [NOTES]: ' \n' }) }),
      /están vacías/,
    );
    assert.deepEqual(calls, []);
  });
});

describe('release-tag · línea de comandos', () => {
  function runMain(responses, env = ENV) {
    const { run } = setup(responses);
    const logs = [];
    const errors = [];
    const status = main({
      argv: ARGS,
      env,
      run,
      readFile,
      log: (line) => logs.push(line),
      error: (line) => errors.push(line),
    });
    return { status, logs, errors };
  }

  it('termina en 0 y cuenta lo que hizo', () => {
    const { status, logs } = runMain({ [REF]: commitRef(COMMIT), [RELEASE]: release() });
    assert.equal(status, 0);
    assert.equal(logs.length, 2);
  });

  it('al fallar termina en 1 y, en GitHub Actions, con una anotación de error en una línea', () => {
    const { status, errors } = runMain(
      { [REF]: result(1, '', 'gh: %0A::error::otra\n(HTTP 500)') },
      { ...ENV, GITHUB_ACTIONS: 'true' },
    );
    assert.equal(status, 1);
    assert.equal(errors.length, 1);
    assert.match(errors[0], /^::error::/);
    assert.equal(errors[0].includes('\n'), false);
    assert.match(errors[0], /%250A/);
    assert.match(errors[0], /Si falla «etiquetar»/);
  });

  it('un error que no es de la lógica se propaga', () => {
    assert.throws(
      () =>
        main({
          argv: ARGS,
          env: ENV,
          run: () => {
            throw new Error('inesperado');
          },
          readFile,
          log: () => {},
          error: () => {},
        }),
      /inesperado/,
    );
  });

  it('funciona solo, copiado a un directorio sin los demás scripts (artefacto de `decidir`)', () => {
    const directory = mkdtempSync(join(tmpdir(), 'release-tag-'));
    copyFileSync(
      fileURLToPath(new URL('./release-tag.mjs', import.meta.url)),
      join(directory, 'release-tag.mjs'),
    );
    writeFileSync(join(directory, NOTES), 'notas\n');
    assert.deepEqual(readdirSync(directory).sort(), [NOTES, 'release-tag.mjs']);
    // Sin GITHUB_REPOSITORY falla antes de llamar a GitHub: prueba que el módulo carga solo.
    assert.throws(
      () =>
        execFileSync(
          process.execPath,
          [join(directory, 'release-tag.mjs'), ...ARGS.slice(0, 3), NOTES],
          {
            cwd: directory,
            env: { PATH: process.env.PATH },
            stdio: 'pipe',
          },
        ),
      (error) => /GITHUB_REPOSITORY/.test(String(error.stderr)),
    );
  });
});
