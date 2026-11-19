// Decides what the release workflow (ui-release.yml, job `decidir`) does with the current commit of main:
// `publicar`, `etiquetar`, `nada` or `error` (feature 002, spec.md › Diseño técnico, table of outputs).
// Runs from ui/ after a checkout with fetch-depth 2. It installs nothing and only reads: npm view, git
// rev-parse/cat-file/show/log and git ls-remote. It never publishes, tags or pushes.
//
// Usage: node scripts/release-decide.mjs
// With GITHUB_OUTPUT set, it appends salida, nombre, version, commit, node, npm and mensaje to it.
// Exit code: 0 for publicar, etiquetar and nada; 1 for error.
import { execFileSync } from 'node:child_process';
import { appendFileSync, readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export const LIBRARY_NAME = 'liv-components';
export const LIBRARY_MANIFEST = 'projects/liv-components/package.json';
// Minimum versions for npm trusted publishing (OIDC).
export const MIN_NODE = '22.14.0';
export const MIN_NPM = '11.5.1';

const EXACT_VERSION = /^\d+\.\d+\.\d+$/;
const COMMIT_SHA = /^[0-9a-f]{40}$/;

class DecisionError extends Error {}

function fail(message) {
  throw new DecisionError(message);
}

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

function readJson(readFile, path) {
  try {
    return JSON.parse(readFile(path));
  } catch (cause) {
    fail(`No se pudo leer ui/${path}: ${cause.message}`);
  }
}

function readLibrary(readFile) {
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

function readToolVersions(readFile) {
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
  if (!npm) {
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

  const parentManifest = `HEAD^1:./${LIBRARY_MANIFEST}`;
  let parentVersion = null;
  if (run('git', ['cat-file', '-e', parentManifest]).status === 0) {
    const shown = run('git', ['show', parentManifest]);
    try {
      if (shown.status !== 0) throw new Error(shown.stderr);
      parentVersion = JSON.parse(shown.stdout).version;
    } catch (cause) {
      fail(`No se pudo leer la versión del commit padre: ${cause.message}`);
    }
  }
  return { commit, bumped: parentVersion !== version };
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

function pendingMessage(run, version) {
  const log = run('git', [
    'log',
    '-1',
    '--format=%h',
    `-S"version": "${version}"`,
    '--',
    LIBRARY_MANIFEST,
  ]);
  const bumpCommit = log.status === 0 ? log.stdout.trim() : '';
  const where = bumpCommit ? `del commit ${bumpCommit}` : 'del commit que la subió';
  return `Nada que publicar: este commit no subió la versión. La versión ${version} está pendiente en la ejecución ${where}; solo ese commit la publica.`;
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
      if (foreignTag) {
        fail(
          `La etiqueta ui-v${version} ya existe en el commit ${tagCommit}, no en este (${commit}). No se mueve.`,
        );
      }
      if (tagCommit === commit) {
        return {
          ...facts,
          salida: 'nada',
          mensaje: `Nada que hacer: ${name}@${version} ya está publicada y etiquetada en este commit.`,
        };
      }
      return {
        ...facts,
        salida: 'etiquetar',
        mensaje: `${name}@${version} ya está publicada por este commit y falta su etiqueta: se etiqueta.`,
      };
    }

    if (latest !== null && compareVersions(version, latest) < 0) {
      fail(
        `La versión ${version} es menor que la última publicada (${latest}) y no se publicó. Sube la versión por encima de ${latest}.`,
      );
    }
    if (!bumped) {
      return { ...facts, salida: 'nada', mensaje: pendingMessage(run, version) };
    }
    if (foreignTag) {
      fail(
        `La etiqueta ui-v${version} ya existe en el commit ${tagCommit}, no en este (${commit}). No se mueve.`,
      );
    }
    return {
      ...facts,
      salida: 'publicar',
      mensaje: `Se publica ${name}@${version} desde el commit ${commit}, con la aprobación del entorno npm.`,
    };
  } catch (error) {
    if (error instanceof DecisionError) return { salida: 'error', mensaje: error.message };
    throw error;
  }
}

function singleLine(value) {
  return String(value).replace(/[\r\n]+/g, ' ');
}

export function main({ run, readFile, env, log, error, appendFile = appendFileSync }) {
  const decision = decide({ run, readFile });
  const report = decision.salida === 'error' ? error : log;
  const prefix = decision.salida === 'error' && env.GITHUB_ACTIONS === 'true' ? '::error::' : '';
  report(`${prefix}${singleLine(decision.mensaje)}`);

  if (env.GITHUB_OUTPUT) {
    const keys = ['salida', 'nombre', 'version', 'commit', 'node', 'npm', 'mensaje'];
    const lines = keys
      .filter((key) => decision[key] !== undefined)
      .map((key) => `${key}=${singleLine(decision[key])}\n`);
    appendFile(env.GITHUB_OUTPUT, lines.join(''));
  }
  return decision.salida === 'error' ? 1 : 0;
}

function runCommand(command, args) {
  try {
    const stdout = execFileSync(command, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      // npm is a .cmd script on Windows, which only runs through a shell. Its arguments are fixed or
      // validated (X.Y.Z), so they carry no shell syntax.
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
    run: runCommand,
    readFile: (path) => readFileSync(path, 'utf8'),
    env: process.env,
    log: console.log,
    error: console.error,
  });
}
