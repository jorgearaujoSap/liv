// Decides what the release workflow (ui-release.yml, job `decidir`) does with the current commit of main:
// `publicar`, `etiquetar`, `nada` or `error` (feature 002, spec.md › Diseño técnico, table of outputs).
// Runs from ui/ after a checkout with fetch-depth 2. It installs nothing and only reads: npm view, git
// rev-parse/ls-tree/show and git ls-remote. It never publishes, tags or pushes. Every failed answer from
// npm or git is an error: it never resolves to `publicar` or `nada`.
//
// Usage: node scripts/release-decide.mjs
// With GITHUB_OUTPUT set, it appends salida, nombre, version, commit, node, npm and mensaje to it.
// Exit code: 0 for publicar, etiquetar and nada; 1 for error.
import { execFileSync } from 'node:child_process';
import { appendFileSync, readFileSync, realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export const LIBRARY_NAME = 'liv-components';
export const LIBRARY_MANIFEST = 'projects/liv-components/package.json';
// Minimum versions for npm trusted publishing (OIDC).
export const MIN_NODE = '22.14.0';
export const MIN_NPM = '11.5.1';

// X.Y.Z as SemVer defines it: no leading zeros, no pre-release or build suffix.
export const EXACT_VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const COMMIT_SHA = /^[0-9a-f]{40}$/;
// Where the person finds how to resolve each failure (feature 002, 002-04).
const GUIDE = 'Guía: README.md de liv-components › «Publicar una versión» › «Si falla «decidir»».';

/** A check that failed with a message that says how to fix it. */
export class ReleaseError extends Error {}

function fail(message) {
  throw new ReleaseError(message);
}

/** Compares two X.Y.Z versions (validate them with EXACT_VERSION first). */
export function compareVersions(left, right) {
  const [a, b] = [left, right].map((value) => value.split('.').map(Number));
  for (let i = 0; i < 3; i++) {
    if (a[i] !== b[i]) return a[i] - b[i];
  }
  return 0;
}

/** Highest X.Y.Z among the published versions and the `latest` dist-tag (pre-releases do not count). */
export function latestStable(versions, latestTag) {
  const stable = [...versions, latestTag].filter((value) => EXACT_VERSION.test(value ?? ''));
  return stable.sort(compareVersions).at(-1) ?? null;
}

/** Replaces line breaks, so a value can never add a line to GITHUB_OUTPUT or to the log. */
export function singleLine(value) {
  return String(value).replace(/[\r\n]+/g, ' ');
}

/** Message as a GitHub Actions error annotation, escaped so it cannot carry another workflow command. */
export function errorAnnotation(message) {
  const escaped = singleLine(message).replace(/%/g, '%25');
  return `::error::${escaped}`;
}

function readJson(readFile, path) {
  try {
    return JSON.parse(readFile(path));
  } catch (cause) {
    fail(`No se pudo leer ui/${path}: ${cause.message}`);
  }
}

/** Name and version of the library; fails unless the name is liv-components and the version X.Y.Z. */
export function readLibrary(readFile) {
  const { name, version } = readJson(readFile, LIBRARY_MANIFEST);
  if (name !== LIBRARY_NAME) {
    fail(`El paquete se llama "${name}" y el workflow solo publica ${LIBRARY_NAME}.`);
  }
  if (typeof version !== 'string' || !EXACT_VERSION.test(version)) {
    fail(
      `La versión "${version}" no es X.Y.Z. Usa npm run version:patch, version:minor o version:major.`,
    );
  }
  return { name, version };
}

/** Exact Node (ui/.nvmrc) and npm (packageManager) versions that the publish job installs. */
export function readToolVersions(readFile) {
  let node;
  try {
    node = readFile('.nvmrc').trim();
  } catch (cause) {
    fail(`No se pudo leer ui/.nvmrc: ${cause.message}`);
  }
  if (!EXACT_VERSION.test(node)) {
    fail(
      `ui/.nvmrc dice "${node}": debe ser una versión exacta de Node (X.Y.Z), ${MIN_NODE} o mayor.`,
    );
  }
  if (compareVersions(node, MIN_NODE) < 0) {
    fail(
      `ui/.nvmrc dice ${node}: la publicación de confianza de npm pide Node ${MIN_NODE} o mayor.`,
    );
  }

  const { packageManager } = readJson(readFile, 'package.json');
  const npm = /^npm@(\d+\.\d+\.\d+)$/.exec(packageManager ?? '')?.[1];
  if (!npm || !EXACT_VERSION.test(npm)) {
    fail(
      `El campo packageManager de ui/package.json ("${packageManager}") debe ser npm@X.Y.Z, con la versión exacta.`,
    );
  }
  if (compareVersions(npm, MIN_NPM) < 0) {
    fail(
      `packageManager dice npm@${npm}: la publicación de confianza pide npm ${MIN_NPM} o mayor.`,
    );
  }
  return { node, npm };
}

/** Version of the library in the parent commit, or null if the parent did not have the file. */
function readParentVersion(run) {
  const listed = run('git', ['ls-tree', 'HEAD^1', '--', LIBRARY_MANIFEST]);
  if (listed.status !== 0) {
    fail(`No se pudo leer el árbol del commit padre (git ls-tree): ${listed.stderr.trim()}`);
  }
  if (!listed.stdout.trim()) return null;
  const shown = run('git', ['show', `HEAD^1:./${LIBRARY_MANIFEST}`]);
  try {
    if (shown.status !== 0) throw new Error(shown.stderr.trim() || `código ${shown.status}`);
    return JSON.parse(shown.stdout).version ?? null;
  } catch (cause) {
    fail(`No se pudo leer la versión del commit padre: ${cause.message}`);
  }
}

/**
 * The current commit and whether it bumped the version: the parent had no library, a version that is
 * not X.Y.Z, or a lower one. A commit that lowers the version (a revert) did not bump it.
 */
function readCommits(run, version) {
  const head = run('git', ['rev-parse', 'HEAD']);
  const commit = head.stdout.trim();
  if (head.status !== 0 || !COMMIT_SHA.test(commit)) {
    fail('No se pudo leer el commit actual (git rev-parse HEAD).');
  }
  const parent = run('git', ['rev-parse', '--verify', '--quiet', 'HEAD^1^{commit}']);
  if (parent.status !== 0) {
    fail('Falta el commit padre: el checkout debe hacerse con fetch-depth: 2.');
  }
  const parentVersion = readParentVersion(run);
  const bumped =
    typeof parentVersion !== 'string' ||
    !EXACT_VERSION.test(parentVersion) ||
    compareVersions(version, parentVersion) > 0;
  return { commit, bumped };
}

function readPublished(run, name) {
  const view = run('npm', ['view', name, 'versions', 'dist-tags', '--json', '--prefer-online']);
  let versions;
  let latestTag;
  try {
    if (view.status !== 0) throw new Error(view.stderr.trim() || `código ${view.status}`);
    const parsed = JSON.parse(view.stdout);
    versions = [].concat(parsed.versions ?? []);
    latestTag = parsed['dist-tags']?.latest;
    if (versions.length === 0) throw new Error('la lista de versiones está vacía');
  } catch (cause) {
    fail(
      `npm no respondió con las versiones de ${name} (${cause.message}). Sin esa respuesta no se decide nada: vuelve a ejecutar cuando npm responda.`,
    );
  }
  return { versions, latest: latestStable(versions, latestTag) };
}

/** Commit the remote tag ui-v<version> points to (the peeled commit for annotated tags), or null. */
function readTagCommit(run, version) {
  const ref = `refs/tags/ui-v${version}`;
  const listed = run('git', ['ls-remote', '--tags', 'origin', ref, `${ref}^{}`]);
  if (listed.status !== 0) {
    fail(`No se pudieron leer las etiquetas remotas (git ls-remote): ${listed.stderr.trim()}`);
  }
  const refs = new Map(
    listed.stdout
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => line.split('\t').reverse()),
  );
  return refs.get(`${ref}^{}`) ?? refs.get(ref) ?? null;
}

/**
 * Fails if npm records that the version was published from another commit (gitHead, present when it was
 * published by hand from a Git checkout). Without gitHead, the bump of this commit is the evidence.
 */
function checkPublishedFrom(run, name, version, commit) {
  const view = run('npm', ['view', `${name}@${version}`, 'gitHead', '--json', '--prefer-online']);
  let gitHead = null;
  try {
    if (view.status !== 0) throw new Error(view.stderr.trim() || `código ${view.status}`);
    gitHead = view.stdout.trim() ? JSON.parse(view.stdout) : null;
  } catch (cause) {
    fail(`npm no respondió de qué commit se publicó ${name}@${version} (${cause.message}).`);
  }
  if (gitHead !== null && gitHead !== commit) {
    fail(
      `${name}@${version} se publicó desde el commit ${gitHead}, no desde este (${commit}): no se etiqueta este commit.`,
    );
  }
}

function foreignTagError(version, tagCommit, commit) {
  return `La etiqueta ui-v${version} ya existe en el commit ${tagCommit}, no en este (${commit}). No se mueve.`;
}

/**
 * Pure decision over the injected `run(command, args)` and `readFile(path)`.
 * Returns { salida, mensaje, nombre?, version?, commit?, node?, npm? }.
 */
export function decide({ run, readFile }) {
  try {
    const { name, version } = readLibrary(readFile);
    const tools = readToolVersions(readFile);
    const { commit, bumped } = readCommits(run, version);
    const { versions, latest } = readPublished(run, name);
    const tagCommit = readTagCommit(run, version);
    const facts = { nombre: name, version, commit, ...tools };
    const foreignTag = tagCommit !== null && tagCommit !== commit;

    if (versions.includes(version)) {
      if (!bumped) {
        return {
          ...facts,
          salida: 'nada',
          mensaje: `Nada que publicar: ${name}@${version} ya está publicada.`,
        };
      }
      if (foreignTag) fail(foreignTagError(version, tagCommit, commit));
      checkPublishedFrom(run, name, version, commit);
      // Also with the tag already on this commit: the release may be missing (a re-run of every job after a
      // failed `etiquetar`). release-tag.mjs creates only what is missing and changes nothing that exists.
      return {
        ...facts,
        salida: 'etiquetar',
        mensaje: `${name}@${version} ya está publicada por este commit y falta su etiqueta o su release: se crea lo que falte, si falta algo.`,
      };
    }

    if (latest !== null && compareVersions(version, latest) < 0) {
      fail(
        `La versión ${version} es menor que la última publicada (${latest}) y no se publicó. Sube la versión por encima de ${latest}.`,
      );
    }
    if (!bumped) {
      // With fetch-depth 2 the history does not say which commit bumped it, so no commit is named.
      return {
        ...facts,
        salida: 'nada',
        mensaje: `Nada que publicar: este commit no subió la versión. La versión ${version} está pendiente en la ejecución del commit que la subió; solo ese commit la publica.`,
      };
    }
    if (foreignTag) fail(foreignTagError(version, tagCommit, commit));
    return {
      ...facts,
      salida: 'publicar',
      mensaje: `Se publica ${name}@${version} desde el commit ${commit}, con la aprobación del entorno npm.`,
    };
  } catch (error) {
    if (error instanceof ReleaseError) return { salida: 'error', mensaje: error.message };
    throw error;
  }
}

export function main({ run, readFile, env, log, error, appendFile = appendFileSync }) {
  const decision = decide({ run, readFile });
  if (decision.salida === 'error') {
    const message = `${decision.mensaje} ${GUIDE}`;
    error(env.GITHUB_ACTIONS === 'true' ? errorAnnotation(message) : singleLine(message));
  } else {
    log(singleLine(decision.mensaje));
  }

  if (env.GITHUB_OUTPUT) {
    const keys = ['salida', 'nombre', 'version', 'commit', 'node', 'npm', 'mensaje'];
    const lines = keys
      .filter((key) => decision[key] !== undefined)
      .map((key) => `${key}=${singleLine(decision[key])}\n`);
    appendFile(env.GITHUB_OUTPUT, lines.join(''));
  }
  return decision.salida === 'error' ? 1 : 0;
}

/** Runs a command without a shell (npm, on Windows, through one: it is a .cmd script). */
export function runCommand(command, args) {
  try {
    const stdout = execFileSync(command, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      // The arguments that reach the Windows shell are fixed or validated (name and X.Y.Z).
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

/** True when this module is the script node runs, also through a symbolic link or a junction. */
export function isEntryPoint(moduleUrl) {
  if (!process.argv[1]) return false;
  try {
    return realpathSync(process.argv[1]) === realpathSync(fileURLToPath(moduleUrl));
  } catch {
    return false;
  }
}

if (isEntryPoint(import.meta.url)) {
  process.exitCode = main({
    run: runCommand,
    readFile: (path) => readFileSync(path, 'utf8'),
    env: process.env,
    log: console.log,
    error: console.error,
  });
}
