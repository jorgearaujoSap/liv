// Pre-release checks for liv-components. Each failed check explains how to fix it; nothing is published
// until all pass.
//
//   node scripts/check-release.mjs                  manual publish: npm runs it before `npm run release`
//                                                   ("prerelease" script), on the publisher's machine.
//   node scripts/check-release.mjs --ci [--paquete <file.tgz>]
//                                                   CI (feature 002, 002-10): no npm whoami, npm owner or
//                                                   local branch. It also checks what `decidir` will check
//                                                   (exact Node and npm) and the packed package.json of the
//                                                   .tgz (default dist/liv-components-<version>.tgz, from
//                                                   `npm run pack`), so a release fails in its PR, not in main.
import { readFileSync } from 'node:fs';

import {
  ReleaseError,
  compareVersions,
  isEntryPoint,
  latestStable,
  readLibrary,
  readToolVersions,
  runCommand,
  singleLine,
} from './release-decide.mjs';
import { extractSection } from './release-notes.mjs';
import { readPackedManifest } from './release-publish.mjs';

const LIBRARY_DIR = 'projects/liv-components';
const RELEASE_BRANCH = 'main';

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
    else {
      throw new Error(
        `Argumento desconocido: ${argv[i]}. Uso: check-release.mjs [--ci [--paquete <archivo.tgz>]]`,
      );
    }
  }
  if (options.tarball && !options.ci) throw new Error('--paquete solo se usa con --ci.');
  return options;
}

/** Name and version for the message when readLibrary failed (its problem already says why). */
function describeInvalidLibrary(readFile) {
  try {
    const { name, version } = JSON.parse(readFile(`${LIBRARY_DIR}/package.json`));
    return { name, version };
  } catch {
    return { name: 'liv-components', version: '?' };
  }
}

function output(result) {
  return result.status === 0 ? result.stdout.trim() : null;
}

/** Runs a check that throws ReleaseError (from release-decide) and records its message. */
function collect(problems, check) {
  try {
    return check();
  } catch (cause) {
    if (!(cause instanceof ReleaseError)) throw cause;
    problems.push(cause.message);
    return null;
  }
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

function checkPackedManifest({ readBinary, tarball, name, version }, problems) {
  let packed;
  try {
    packed = readPackedManifest(readBinary(tarball));
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

function checkPublished({ run, name, version }, problems) {
  try {
    const view = run('npm', ['view', name, 'versions', 'dist-tags', '--json', '--prefer-online']);
    if (view.status !== 0) throw new Error(view.stderr.trim() || `código ${view.status}`);
    const parsed = JSON.parse(view.stdout);
    const versions = [].concat(parsed.versions ?? []);
    if (versions.length === 0) throw new Error('la lista de versiones está vacía');
    const latest = latestStable(versions, parsed['dist-tags']?.latest);
    if (versions.includes(version)) {
      problems.push(
        `La versión ${version} ya está publicada y no se puede reemplazar. Sube la versión (npm run version:patch|minor|major), anótala en CHANGELOG.md e intégrala por PR.`,
      );
    } else if (latest && compareVersions(version, latest) <= 0) {
      problems.push(`La versión ${version} no es mayor que la última publicada (${latest}).`);
    }
    return versions;
  } catch (cause) {
    problems.push(
      `npm no respondió con las versiones publicadas de ${name} (${cause.message}). Sin esa respuesta no se sabe si ${version} es nueva: repite cuando npm responda.`,
    );
    return [];
  }
}

/**
 * Returns { name, version, user, problems } over the injected run(command, args), readFile(path) (text)
 * and readBinary(path) (the .tgz).
 */
export function checkRelease({ ci, tarball, run, readFile, readBinary }) {
  const problems = [];
  const library = collect(problems, () => readLibrary(readFile));
  const { name, version } = library ?? describeInvalidLibrary(readFile);
  if (ci) collect(problems, () => readToolVersions(readFile));

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

  // npm is asked only about a valid name and version: they reach a shell on Windows.
  const publishedVersions = library ? checkPublished({ run, name, version }, problems) : [];

  if (!ci && user && publishedVersions.length > 0) {
    const owners = output(run('npm', ['owner', 'ls', name])) ?? '';
    if (!owners.split(/\r?\n/).some((line) => line.split(' ')[0] === user)) {
      problems.push(
        `Tu usuario de npm (${user}) no puede publicar ${name}. Pide a un responsable actual que ejecute: npm owner add ${user} ${name}`,
      );
    }
  }

  // The same extraction as the release notes: a missing or empty section fails here, in the PR.
  if (library) {
    try {
      extractSection(readFile('CHANGELOG.md'), version);
    } catch (cause) {
      problems.push(cause.message);
    }
  }

  if (ci && library) {
    checkPackedManifest(
      { readBinary, tarball: tarball ?? `dist/${name}-${version}.tgz`, name, version },
      problems,
    );
  }

  return { name, version, user, problems };
}

export function main({ argv, run, readFile, readBinary, log, error }) {
  let options;
  try {
    options = parseArgs(argv);
  } catch (cause) {
    error(cause.message);
    return 1;
  }
  const { name, version, user, problems } = checkRelease({
    ...options,
    run,
    readFile,
    readBinary,
  });
  if (problems.length > 0) {
    error(`\nNo se publica ${name}@${version}:\n`);
    // One line per problem: npm's output inside a message cannot add lines to the CI log.
    problems.forEach((problem, index) => error(`  ${index + 1}. ${singleLine(problem)}`));
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

if (isEntryPoint(import.meta.url)) {
  process.exitCode = main({
    argv: process.argv.slice(2),
    run: runCommand,
    readFile: (path) => readFileSync(path, 'utf8'),
    readBinary: (path) => readFileSync(path),
    log: console.log,
    error: console.error,
  });
}
