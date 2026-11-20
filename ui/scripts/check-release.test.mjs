// Tests of the pre-release checks (feature 002, 002-04 and 002-10): the --ci mode and the manual mode.
// npm and git are simulated, and the .tgz is built in memory.
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { EXPECTED_REPOSITORY, checkRelease, main, parseArgs } from './check-release.mjs';
import { fakeFiles, fakeRun, makeTarball, npmError, result } from './test-helpers.mjs';

const VIEW = 'npm view liv-components versions dist-tags --json --prefer-online';
const PUBLISHED = result(
  0,
  JSON.stringify({ versions: ['0.0.0-stage', '0.4.0', '0.5.0'], 'dist-tags': { latest: '0.5.0' } }),
);
const CHANGELOG =
  '# Changelog\n\n## [Sin publicar]\n\n## [0.6.0] — 2026-11-20\n\n- algo\n\n## [0.5.0]\n\n- otro\n';

function packed(overrides = {}) {
  return makeTarball({
    'package/package.json': JSON.stringify({
      name: 'liv-components',
      version: '0.6.0',
      repository: EXPECTED_REPOSITORY,
      ...overrides,
    }),
  });
}

function setup({
  version = '0.6.0',
  name = 'liv-components',
  changelog = CHANGELOG,
  nvmrc = '22.23.5\n',
  packageManager = 'npm@11.12.0',
  view = PUBLISHED,
  tgz = packed(),
  tarball = `dist/liv-components-${version}.tgz`,
  status = result(0, ''),
  branch = result(0, 'main\n'),
  whoami = result(0, 'jorge\n'),
  owners = result(0, 'jorge <correo>\n'),
} = {}) {
  const { run, calls } = fakeRun({
    [VIEW]: view,
    'git status --porcelain -- .': status,
    'git rev-parse --abbrev-ref HEAD': branch,
    'npm whoami': whoami,
    'npm owner ls liv-components': owners,
  });
  const readFile = fakeFiles({
    'projects/liv-components/package.json': JSON.stringify({ name, version }),
    'CHANGELOG.md': changelog,
    '.nvmrc': nvmrc,
    'package.json': JSON.stringify({ packageManager }),
  });
  const readBinary = fakeFiles(tgz ? { [tarball]: tgz } : {});
  return { run, calls, readFile, readBinary };
}

function problemsOf(options, mode = { ci: true }) {
  const { run, readFile, readBinary, calls } = setup(options);
  return { ...checkRelease({ ...mode, run, readFile, readBinary }), calls };
}

function assertProblem(problems, message) {
  assert.ok(
    problems.some((problem) => message.test(problem)),
    `${message} no está en:\n${problems.join('\n')}`,
  );
}

describe('check-release --ci · una versión lista pasa (002-10)', () => {
  it('sin problemas, y sin npm whoami, npm owner ni la rama local', () => {
    const { problems, calls } = problemsOf();
    assert.deepEqual(problems, []);
    assert.ok(
      !calls.some((call) => /npm whoami|npm owner|rev-parse --abbrev-ref/.test(call)),
      calls.join('\n'),
    );
  });

  it('lee el .tgz indicado con --paquete', () => {
    const path = 'otro/dir/liv-components-0.6.0.tgz';
    const { run, readFile, readBinary } = setup({ tarball: path });
    const { problems } = checkRelease({ ci: true, tarball: path, run, readFile, readBinary });
    assert.deepEqual(problems, []);
  });
});

describe('check-release --ci · cada fallo da su mensaje (002-04)', () => {
  const failures = {
    'falta la sección del CHANGELOG.md': [
      { changelog: '## [0.5.0]\n\n- x\n' },
      /no tiene la sección "## \[0\.6\.0\]"/,
    ],
    'la sección del CHANGELOG.md está vacía': [
      { changelog: '## [0.6.0] — 2026-11-20\n\n## [0.5.0]\n\n- x\n' },
      /La sección "## \[0\.6\.0\]" de CHANGELOG\.md está vacía/,
    ],
    'la sección es de nivel 3': [
      { changelog: '### [0.6.0]\n\n- x\n' },
      /no tiene la sección "## \[0\.6\.0\]"/,
    ],
    'la versión solo se nombra dentro de un texto': [
      { changelog: '## [Sin publicar]\n\n- Prepara la sección ## [0.6.0]\n' },
      /no tiene la sección "## \[0\.6\.0\]"/,
    ],
    'la versión ya está publicada': [
      { version: '0.5.0', tarball: 'dist/liv-components-0.5.0.tgz' },
      /ya está publicada/,
    ],
    'la versión no es mayor que la última': [
      { version: '0.4.5', changelog: '## [0.4.5]\n\n- x\n' },
      /no es mayor que la última publicada \(0\.5\.0\)/,
    ],
    'la versión no es X.Y.Z': [{ version: '0.6.0-next.1' }, /no es X\.Y\.Z/],
    'el paquete no es liv-components': [{ name: 'otro' }, /El paquete se llama "otro"/],
    '.nvmrc no es una versión exacta': [{ nvmrc: '22\n' }, /ui\/\.nvmrc dice "22"/],
    'packageManager no es npm@X.Y.Z exacto': [
      { packageManager: 'npm@11.12.0+sha512.abc' },
      /packageManager/,
    ],
    'npm no responde': [{ view: result(1, '', 'ETIMEDOUT') }, /npm no respondió/],
    'npm responde E404 por el paquete': [{ view: npmError('E404') }, /npm no respondió/],
    'npm responde vacío': [{ view: result(0, '') }, /npm no respondió/],
    'hay cambios sin commit': [{ status: result(0, ' M src/x.ts\n') }, /cambios sin commit/],
    'no hay .tgz': [{ tgz: null }, /npm run pack/],
    'el .tgz no es un gzip': [
      { tgz: Buffer.from('no es gzip') },
      /No se pudo leer package\/package\.json/,
    ],
    'el paquete empaquetado tiene otra versión': [
      { tgz: packed({ version: '0.5.0' }) },
      /es liv-components@0\.5\.0 y el código dice liv-components@0\.6\.0/,
    ],
    'el paquete empaquetado no tiene repository': [
      { tgz: packed({ repository: undefined }) },
      /campo repository/,
    ],
    'repository es solo un texto': [
      { tgz: packed({ repository: 'github:jorgearaujoSap/liv' }) },
      /campo repository/,
    ],
    'repository apunta a otro repositorio': [
      {
        tgz: packed({
          repository: { ...EXPECTED_REPOSITORY, url: 'git+https://github.com/otro/liv.git' },
        }),
      },
      /campo repository/,
    ],
    'repository sin directory': [
      { tgz: packed({ repository: { type: 'git', url: EXPECTED_REPOSITORY.url } }) },
      /campo repository/,
    ],
  };
  for (const [situation, [options, message]] of Object.entries(failures)) {
    it(situation, () => {
      assertProblem(problemsOf(options).problems, message);
    });
  }

  it('con un nombre o una versión inválidos, no consulta npm', () => {
    for (const options of [{ name: 'otro & calc' }, { version: '1.0.0; rm -rf /' }]) {
      const { calls } = problemsOf(options);
      assert.ok(!calls.some((call) => call.startsWith('npm')), calls.join('\n'));
    }
  });
});

describe('check-release sin --ci · ejecución local, sin publicar', () => {
  it('una versión lista pasa', () => {
    assert.deepEqual(problemsOf({}, { ci: false }).problems, []);
  });

  it('no lee el .tgz ni valida .nvmrc ni packageManager', () => {
    const { problems } = problemsOf({ tgz: null, nvmrc: '22\n' }, { ci: false });
    assert.deepEqual(problems, []);
  });

  const failures = {
    'fuera de main': [{ branch: result(0, 'feat/x\n') }, /Estás en la rama "feat\/x"/],
    'sin sesión de npm': [{ whoami: result(1, '') }, /No has iniciado sesión en npm/],
    'sin permiso en npm': [{ owners: result(0, 'otra <correo>\n') }, /no puede publicar/],
    'npm no responde': [{ view: result(1, '', 'ETIMEDOUT') }, /npm no respondió/],
    'la sección del CHANGELOG.md está vacía': [
      { changelog: '## [0.6.0]\n\n## [0.5.0]\n\n- x\n' },
      /está vacía/,
    ],
  };
  for (const [situation, [options, message]] of Object.entries(failures)) {
    it(situation, () => {
      assertProblem(problemsOf(options, { ci: false }).problems, message);
    });
  }
});

describe('check-release · línea de comandos', () => {
  it('acepta --ci y --ci --paquete <archivo>', () => {
    assert.deepEqual(parseArgs([]), { ci: false, tarball: null });
    assert.deepEqual(parseArgs(['--ci']), { ci: true, tarball: null });
    assert.deepEqual(parseArgs(['--ci', '--paquete', 'x.tgz']), { ci: true, tarball: 'x.tgz' });
  });

  it('rechaza argumentos desconocidos y --paquete sin --ci', () => {
    assert.throws(() => parseArgs(['--cii']), /Argumento desconocido/);
    assert.throws(() => parseArgs(['--paquete', 'x.tgz']), /solo se usa con --ci/);
    assert.throws(() => parseArgs(['--ci', '--paquete']), /Argumento desconocido/);
  });

  it('termina en 0 si todo pasa y en 1, con la lista numerada, si algo falla', () => {
    const logs = [];
    const errors = [];
    const io = { log: (line) => logs.push(line), error: (line) => errors.push(line) };
    assert.equal(main({ argv: ['--ci'], ...setup(), ...io }), 0);
    assert.match(logs[0], /Verificaciones correctas \(CI\): liv-components@0\.6\.0/);
    assert.equal(main({ argv: ['--ci'], ...setup({ changelog: '' }), ...io }), 1);
    assert.match(errors.join('\n'), /1\. CHANGELOG\.md no tiene la sección/);
    assert.match(errors.join('\n'), /Publicar una versión/);
  });

  it('termina en 1 con un argumento desconocido', () => {
    const errors = [];
    const code = main({ argv: ['--cii'], ...setup(), log: () => {}, error: (l) => errors.push(l) });
    assert.equal(code, 1);
    assert.match(errors[0], /Argumento desconocido/);
  });

  it('cada problema va en una sola línea, aunque npm responda en varias', () => {
    const errors = [];
    const failing = setup({ view: result(1, '', 'npm error x\n::add-mask::y\nfin') });
    assert.equal(
      main({ argv: ['--ci'], ...failing, log: () => {}, error: (l) => errors.push(l) }),
      1,
    );
    const numbered = errors.filter((line) => /^ {2}\d+\. /.test(line));
    assert.equal(numbered.length, 1);
    assert.ok(!numbered[0].includes('\n'), numbered[0]);
    assert.ok(
      errors.every((line) => !/^::/m.test(line)),
      errors.join('\n'),
    );
  });

  it('sin projects/liv-components/package.json, da la lista de problemas y no se cae', () => {
    const errors = [];
    const { run } = setup();
    const readFile = fakeFiles({ 'CHANGELOG.md': CHANGELOG });
    const code = main({
      argv: ['--ci'],
      run,
      readFile,
      readBinary: fakeFiles({}),
      log: () => {},
      error: (l) => errors.push(l),
    });
    assert.equal(code, 1);
    assert.match(
      errors.join('\n'),
      /1\. No se pudo leer ui\/projects\/liv-components\/package\.json/,
    );
  });
});
