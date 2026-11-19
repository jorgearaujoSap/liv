// Pre-release checks for liv-components. Each failed check explains how to fix it; nothing is published
// until all pass.
//
//   node scripts/check-release.mjs                  manual publish: npm runs it before `npm run release`
//                                                   ("prerelease" script), on the publisher's machine.
//   node scripts/check-release.mjs --ci [--paquete <file.tgz>]
//                                                   CI (feature 002, 002-10): no npm whoami, npm owner or
//                                                   local branch; checks the packed package.json of the .tgz
//                                                   (default dist/liv-components-<version>.tgz, from
//                                                   `npm run pack`).
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

import { compareVersions, latestStable } from './release-decide.mjs';

const LIBRARY_DIR = 'projects/liv-components';
const RELEASE_BRANCH = 'main';
const EXACT_VERSION = /^\d+\.\d+\.\d+$/;

export const EXPECTED_REPOSITORY = {
  type: 'git',
  url: 'git+https://github.com/jorgearaujoSap/liv.git',
  directory: 'ui/projects/liv-components',
};

export function parseArgs(argv) {
  const options = { ci: false, tarball: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--ci') options.ci = true;
    else if (argv[i] === '--paquete' && argv[i + 1]) options.tarball = argv[++i];
    else
      throw new Error(
        `Argumento desconocido: ${argv[i]}. Uso: check-release.mjs [--ci [--paquete <archivo.tgz>]]`,
      );
  }
  if (options.tarball && !options.ci) throw new Error('--paquete solo se usa con --ci.');
  return options;
}

function output(result) {
  return result.status === 0 ? result.stdout.trim() : null;
}

function checkRepository(manifest, problems) {
  const repository = manifest.repository ?? {};
  const matches = Object.entries(EXPECTED_REPOSITORY).every(
    ([key, value]) => repository[key] === value,
  );
  if (!matches) {
    problems.push(
      `El campo repository del paquete empaquetado es ${JSON.stringify(manifest.repository)} y debe ser ${JSON.stringify(EXPECTED_REPOSITORY)}: npm lo exige para la publicación de confianza. Corrígelo en ${LIBRARY_DIR}/package.json.`,
    );
  }
}

function checkPackedManifest({ run, tarball, name, version }, problems) {
  const extracted = run('tar', ['-xzOf', tarball, 'package/package.json']);
  let packed;
  try {
    if (extracted.status !== 0)
      throw new Error(extracted.stderr.trim() || `código ${extracted.status}`);
    packed = JSON.parse(extracted.stdout);
  } catch (cause) {
    problems.push(
      `No se pudo leer package/package.json de ${tarball} (${cause.message}). Genera el paquete con npm run pack antes de verificar.`,
    );
    return;
  }
  if (packed.name !== name || packed.version !== version) {
    problems.push(
      `El paquete empaquetado es ${packed.name}@${packed.version} y el código dice ${name}@${version}. Vuelve a ejecutar npm run pack.`,
    );
  }
  checkRepository(packed, problems);
}

/** Returns { name, version, user, problems } over the injected run(command, args) and readFile(path). */
export function checkRelease({ ci, tarball, run, readFile }) {
  const { name, version } = JSON.parse(readFile(`${LIBRARY_DIR}/package.json`));
  const problems = [];

  if (!EXACT_VERSION.test(version)) {
    problems.push(
      `La versión "${version}" no es X.Y.Z. Usa npm run version:patch, version:minor o version:major.`,
    );
  }

  if (!ci) {
    const branch = output(run('git', ['rev-parse', '--abbrev-ref', 'HEAD']));
    if (branch !== RELEASE_BRANCH) {
      problems.push(
        `Estás en la rama "${branch}". Solo se publica desde ${RELEASE_BRANCH}, después de integrar el PR: git checkout ${RELEASE_BRANCH} && git pull.`,
      );
    }
  }

  const pendingChanges = output(run('git', ['status', '--porcelain', '--', '.']));
  if (pendingChanges) {
    problems.push('Hay cambios sin commit en ui/. Publica solo lo que ya está integrado en main.');
  }

  const user = ci ? null : output(run('npm', ['whoami']));
  if (!ci && !user) {
    problems.push('No has iniciado sesión en npm. Ejecuta npm login y repite.');
  }

  let publishedVersions = [];
  try {
    const view = run('npm', ['view', name, 'versions', 'dist-tags', '--json', '--prefer-online']);
    if (view.status !== 0) throw new Error(view.stderr.trim() || `código ${view.status}`);
    const parsed = JSON.parse(view.stdout);
    publishedVersions = [].concat(parsed.versions ?? []);
    if (publishedVersions.length === 0) throw new Error('la lista de versiones está vacía');
    const latest = latestStable(publishedVersions, parsed['dist-tags']?.latest);
    if (publishedVersions.includes(version)) {
      problems.push(
        `La versión ${version} ya está publicada y no se puede reemplazar. Sube la versión (npm run version:patch|minor|major), anótala en CHANGELOG.md e intégrala por PR.`,
      );
    } else if (EXACT_VERSION.test(version) && latest && compareVersions(version, latest) <= 0) {
      problems.push(`La versión ${version} no es mayor que la última publicada (${latest}).`);
    }
  } catch (cause) {
    problems.push(
      `npm no respondió con las versiones publicadas de ${name} (${cause.message}). Sin esa respuesta no se sabe si ${version} es nueva: repite cuando npm responda.`,
    );
  }

  if (!ci && user && publishedVersions.length > 0) {
    const owners = output(run('npm', ['owner', 'ls', name])) ?? '';
    if (!owners.split(/\r?\n/).some((line) => line.split(' ')[0] === user)) {
      problems.push(
        `Tu usuario de npm (${user}) no puede publicar ${name}. Pide a un responsable actual que ejecute: npm owner add ${user} ${name}`,
      );
    }
  }

  if (!readFile('CHANGELOG.md').includes(`## [${version}]`)) {
    problems.push(
      `CHANGELOG.md no tiene la sección "## [${version}]". Describe ahí qué cambia en esta versión.`,
    );
  }

  if (ci) {
    checkPackedManifest(
      { run, tarball: tarball ?? `dist/${name}-${version}.tgz`, name, version },
      problems,
    );
  }

  return { name, version, user, problems };
}

export function main({ argv, run, readFile, log, error }) {
  let options;
  try {
    options = parseArgs(argv);
  } catch (cause) {
    error(cause.message);
    return 1;
  }
  const { name, version, user, problems } = checkRelease({ ...options, run, readFile });
  if (problems.length > 0) {
    error(`\nNo se publica ${name}@${version}:\n`);
    problems.forEach((problem, index) => error(`  ${index + 1}. ${problem}`));
    error('\nProceso completo: ui/README.md, sección "Publicar una versión".\n');
    return 1;
  }
  log(
    options.ci
      ? `Verificaciones correctas (CI): ${name}@${version} se puede publicar.`
      : `Verificaciones correctas: se publicará ${name}@${version} como ${user}.`,
  );
  return 0;
}

function runCommand(command, args) {
  try {
    const stdout = execFileSync(command, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      // npm is a .cmd script on Windows, which only runs through a shell. Its arguments are fixed.
      shell: process.platform === 'win32' && command === 'npm',
    });
    return { status: 0, stdout, stderr: '' };
  } catch (cause) {
    return {
      status: cause.status ?? 1,
      stdout: cause.stdout ?? '',
      stderr: cause.stderr ?? cause.message,
    };
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main({
    argv: process.argv.slice(2),
    run: runCommand,
    readFile: (path) => readFileSync(path, 'utf8'),
    log: console.log,
    error: console.error,
  });
}
