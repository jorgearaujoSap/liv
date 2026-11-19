// Tests of the pre-release checks (feature 002, 002-04 and 002-10): the --ci mode and the manual mode.
// npm, git and tar are simulated.
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { EXPECTED_REPOSITORY, checkRelease, main, parseArgs } from './check-release.mjs';
import { fakeFiles, fakeRun, npmError, result } from './test-helpers.mjs';

const VIEW = 'npm view liv-components versions dist-tags --json --prefer-online';
const PUBLISHED = result(
  0,
  JSON.stringify({ versions: ['0.0.0-stage', '0.4.0', '0.5.0'], 'dist-tags': { latest: '0.5.0' } }),
);
const CHANGELOG =
  '# Changelog\n\n## [Sin publicar]\n\n## [0.6.0] — 2026-11-20\n\n- algo\n\n## [0.5.0]\n';

function packed(overrides = {}) {
  return result(
    0,
    JSON.stringify({
      name: 'liv-components',
      version: '0.6.0',
      repository: EXPECTED_REPOSITORY,
      ...overrides,
    }),
  );
}

function setup({
  version = '0.6.0',
  changelog = CHANGELOG,
  view = PUBLISHED,
  tar = packed(),
  tarball = `dist/liv-components-${version}.tgz`,
  status = result(0, ''),
  branch = result(0, 'main\n'),
  whoami = result(0, 'jorge\n'),
  owners = result(0, 'jorge <correo>\n'),
} = {}) {
  const { run, calls } = fakeRun({
    [VIEW]: view,
    [`tar -xzOf ${tarball} package/package.json`]: tar,
    'git status --porcelain -- .': status,
    'git rev-parse --abbrev-ref HEAD': branch,
    'npm whoami': whoami,
    'npm owner ls liv-components': owners,
  });
  const readFile = fakeFiles({
    'projects/liv-components/package.json': JSON.stringify({ name: 'liv-components', version }),
    'CHANGELOG.md': changelog,
  });
  return { run, calls, readFile };
}

function problemsOf(options, mode = { ci: true }) {
  const { run, readFile, calls } = setup(options);
  return { ...checkRelease({ ...mode, run, readFile }), calls };
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
    const { run, readFile } = setup({ tarball: 'otro/dir/liv-components-0.6.0.tgz' });
    const { problems } = checkRelease({
      ci: true,
      tarball: 'otro/dir/liv-components-0.6.0.tgz',
      run,
      readFile,
    });
    assert.deepEqual(problems, []);
  });
});

describe('check-release --ci · cada fallo da su mensaje (002-04)', () => {
  const failures = {
    'falta la sección del CHANGELOG.md': [
      { changelog: '## [0.5.0]\n' },
      /no tiene la sección "## \[0\.6\.0\]"/,
    ],
    'la versión ya está publicada': [
      { version: '0.5.0', changelog: '## [0.5.0]\n' },
      /ya está publicada/,
    ],
    'la versión no es mayor que la última': [
      { version: '0.4.5', changelog: '## [0.4.5]\n' },
      /no es mayor que la última publicada \(0\.5\.0\)/,
    ],
    'la versión no es X.Y.Z': [
      { version: '0.6.0-next.1', changelog: '## [0.6.0-next.1]\n' },
      /no es X\.Y\.Z/,
    ],
    'npm no responde': [{ view: result(1, '', 'ETIMEDOUT') }, /npm no respondió/],
    'npm responde E404 por el paquete': [{ view: npmError('E404') }, /npm no respondió/],
    'npm responde vacío': [{ view: result(0, '') }, /npm no respondió/],
    'hay cambios sin commit': [{ status: result(0, ' M src/x.ts\n') }, /cambios sin commit/],
    'no hay .tgz': [
      { tar: result(1, '', 'tar: dist/liv-components-0.6.0.tgz: Cannot open') },
      /npm run pack/,
    ],
    'el .tgz no tiene un package.json válido': [
      { tar: result(0, 'no es JSON') },
      /No se pudo leer package\/package\.json/,
    ],
    'el paquete empaquetado tiene otra versión': [
      { tar: packed({ version: '0.5.0' }) },
      /es liv-components@0\.5\.0 y el código dice liv-components@0\.6\.0/,
    ],
    'el paquete empaquetado no tiene repository': [
      { tar: packed({ repository: undefined }) },
      /campo repository/,
    ],
    'repository es solo un texto': [
      { tar: packed({ repository: 'github:jorgearaujoSap/liv' }) },
      /campo repository/,
    ],
    'repository apunta a otro repositorio': [
      {
        tar: packed({
          repository: { ...EXPECTED_REPOSITORY, url: 'git+https://github.com/otro/liv.git' },
        }),
      },
      /campo repository/,
    ],
    'repository sin directory': [
      { tar: packed({ repository: { type: 'git', url: EXPECTED_REPOSITORY.url } }) },
      /campo repository/,
    ],
  };
  for (const [situation, [options, message]] of Object.entries(failures)) {
    it(situation, () => {
      const { problems } = problemsOf(options);
      assert.ok(
        problems.some((problem) => message.test(problem)),
        problems.join('\n'),
      );
    });
  }
});

describe('check-release sin --ci · publicación a mano, como hasta ahora', () => {
  it('una versión lista pasa', () => {
    assert.deepEqual(problemsOf({}, { ci: false }).problems, []);
  });

  it('no lee el .tgz', () => {
    const { calls } = problemsOf({}, { ci: false });
    assert.ok(!calls.some((call) => call.startsWith('tar')), calls.join('\n'));
  });

  const failures = {
    'fuera de main': [{ branch: result(0, 'feat/x\n') }, /Estás en la rama "feat\/x"/],
    'sin sesión de npm': [{ whoami: result(1, '') }, /No has iniciado sesión en npm/],
    'sin permiso en npm': [{ owners: result(0, 'otra <correo>\n') }, /no puede publicar/],
    'npm no responde': [{ view: result(1, '', 'ETIMEDOUT') }, /npm no respondió/],
  };
  for (const [situation, [options, message]] of Object.entries(failures)) {
    it(situation, () => {
      const { problems } = problemsOf(options, { ci: false });
      assert.ok(
        problems.some((problem) => message.test(problem)),
        problems.join('\n'),
      );
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
});
