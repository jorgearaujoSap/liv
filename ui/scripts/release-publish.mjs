// Publishes the verified .tgz of liv-components without publishing twice (feature 002, 002-06 and 002-08).
// Runs in the `publicar` job of ui-release.yml, which has id-token: write, no checkout and no installed
// dependencies: this file travels alone as an artifact of `decidir`, so it imports nothing but Node.
//
//   - the package.json inside the .tgz must be <name>@<version>, the ones it receives;
//   - the version already exists with the same integrity → it continues without publishing (a re-run);
//   - it exists with another integrity → it fails: the published content is not this .tgz;
//   - npm answers that it does not exist (E404) → it checks again that the version is above the highest
//     published one, and runs npm publish <tgz> --ignore-scripts --access public;
//   - any other answer from npm → it fails.
//
// Usage: node release-publish.mjs <file.tgz> <name> <X.Y.Z> [--dry-run]
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { gunzipSync } from 'node:zlib';

const LIBRARY_NAME = 'liv-components';
const EXACT_VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
// A plain relative or absolute path: no shell syntax, no option-like start.
const TARBALL_PATH = /^(?!-)[\w./\\:-]+\.tgz$/;

class PublishError extends Error {}

function fail(message) {
  throw new PublishError(message);
}

function singleLine(value) {
  return String(value).replace(/[\r\n]+/g, ' ');
}

function compareVersions(left, right) {
  const [a, b] = [left, right].map((value) => value.split('.').map(Number));
  for (let i = 0; i < 3; i++) {
    if (a[i] !== b[i]) return a[i] - b[i];
  }
  return 0;
}

export function parseArgs(argv) {
  const dryRun = argv.at(-1) === '--dry-run';
  const positional = dryRun ? argv.slice(0, -1) : argv;
  if (positional.length !== 3) {
    fail('Uso: node release-publish.mjs <archivo.tgz> <nombre> <X.Y.Z> [--dry-run]');
  }
  const [tarball, name, version] = positional;
  if (name !== LIBRARY_NAME) fail(`Solo se publica ${LIBRARY_NAME}, no "${name}".`);
  if (!EXACT_VERSION.test(version)) fail(`La versión "${version}" no es X.Y.Z.`);
  if (!TARBALL_PATH.test(tarball)) fail(`"${tarball}" no es la ruta de un archivo .tgz.`);
  return { tarball, name, version, dryRun };
}

/** Subresource integrity of the tarball, as npm stores it in dist.integrity. */
export function integrityOf(content) {
  return `sha512-${createHash('sha512').update(content).digest('base64')}`;
}

/** package/package.json of an npm .tgz (gzip + ustar), read with Node only. */
export function readPackedManifest(tgz) {
  const tar = gunzipSync(tgz);
  const found = [];
  let offset = 0;
  while (offset + 512 <= tar.length) {
    const header = tar.subarray(offset, offset + 512);
    if (header.every((byte) => byte === 0)) break;
    const field = (start, length) =>
      header
        .subarray(start, start + length)
        .toString('utf8')
        .replace(/\0[\s\S]*$/, '');
    const size = parseInt(field(124, 12).trim() || '0', 8);
    if (!Number.isFinite(size)) throw new Error('el .tgz tiene una cabecera de tar inválida');
    const prefix = field(345, 155);
    const path = prefix ? `${prefix}/${field(0, 100)}` : field(0, 100);
    const type = field(156, 1);
    const body = offset + 512;
    if ((type === '0' || type === '') && path === 'package/package.json') {
      found.push(tar.subarray(body, body + size).toString('utf8'));
    }
    offset = body + Math.ceil(size / 512) * 512;
  }
  if (found.length !== 1) {
    throw new Error(`el .tgz tiene ${found.length} archivos package/package.json, no uno`);
  }
  return JSON.parse(found[0]);
}

function npmErrorCode(stdout) {
  try {
    return JSON.parse(stdout)?.error?.code ?? null;
  } catch {
    return null;
  }
}

function npmFailure(view) {
  return singleLine(view.stderr.trim() || `código ${view.status}`);
}

/** dist.integrity of the published version, or null if npm answers E404 (it does not exist). */
function publishedIntegrity(run, name, version) {
  const view = run('npm', [
    'view',
    `${name}@${version}`,
    'dist.integrity',
    '--json',
    '--prefer-online',
  ]);
  if (view.status !== 0) {
    if (npmErrorCode(view.stdout) === 'E404') return null;
    fail(
      `npm no respondió si ${name}@${version} existe (${npmFailure(view)}). No se publica: vuelve a ejecutar cuando npm responda.`,
    );
  }
  let integrity;
  try {
    integrity = JSON.parse(view.stdout);
  } catch {
    integrity = null;
  }
  if (typeof integrity !== 'string' || !integrity.startsWith('sha512-')) {
    fail(
      `npm respondió sin la integridad de ${name}@${version}: "${singleLine(view.stdout.trim())}". No se publica.`,
    );
  }
  return integrity;
}

/**
 * Fails unless npm lists the package's versions and <version> is above the highest X.Y.Z among them.
 * A re-run of an old `publicar` must not publish a version below one published in the meantime (002-05).
 */
function checkAboveLatest(run, name, version) {
  const view = run('npm', ['view', name, 'versions', '--json', '--prefer-online']);
  let versions;
  try {
    if (view.status !== 0) throw new Error(npmFailure(view));
    versions = [].concat(JSON.parse(view.stdout) ?? []);
    if (versions.length === 0) throw new Error('la lista de versiones está vacía');
  } catch (cause) {
    fail(
      `npm no respondió con las versiones de ${name} (${singleLine(cause.message)}). No se publica: vuelve a ejecutar cuando npm responda.`,
    );
  }
  if (versions.includes(version)) {
    fail(`npm lista ${name}@${version} como publicada, pero no da su integridad. No se publica.`);
  }
  const stable = versions.filter((value) => EXACT_VERSION.test(value)).sort(compareVersions);
  const latest = stable.at(-1);
  if (latest && compareVersions(version, latest) <= 0) {
    fail(
      `${name}@${version} no es mayor que la última publicada (${latest}). No se publica: sube la versión en un PR nuevo.`,
    );
  }
}

/** Returns the message of what it did; throws PublishError if it must fail. */
export function publish({ argv, run, readFile }) {
  const { tarball, name, version, dryRun } = parseArgs(argv);
  const mode = dryRun ? 'DRY-RUN: no publica. ' : '';
  let local;
  let packed;
  try {
    const content = readFile(tarball);
    local = integrityOf(content);
    packed = readPackedManifest(content);
  } catch (cause) {
    fail(`No se pudo leer ${tarball}: ${singleLine(cause.message)}. No se publica.`);
  }
  if (packed.name !== name || packed.version !== version) {
    fail(
      `El .tgz es ${packed.name}@${packed.version} y se pidió publicar ${name}@${version}. No se publica.`,
    );
  }

  const published = publishedIntegrity(run, name, version);
  if (published === local) {
    return `${mode}${name}@${version} ya está publicada con la misma integridad: no se publica otra vez.`;
  }
  if (published !== null) {
    fail(
      `${name}@${version} ya está publicada con otra integridad (${published}); este paquete tiene ${local}. No se publica: el contenido publicado no es el que se verificó.`,
    );
  }
  checkAboveLatest(run, name, version);

  const args = ['publish', tarball, '--ignore-scripts', '--access', 'public'];
  if (dryRun) args.push('--dry-run');
  const outcome = run('npm', args);
  if (outcome.status !== 0) {
    fail(`npm publish falló (${npmFailure(outcome)}).`);
  }
  return dryRun
    ? `${mode}npm publish --dry-run de ${name}@${version} (${local}) terminó bien.`
    : `Publicada ${name}@${version} (${local}).`;
}

export function main({ argv, run, readFile, env, log, error }) {
  try {
    log(publish({ argv, run, readFile }));
    return 0;
  } catch (cause) {
    if (!(cause instanceof PublishError)) throw cause;
    const message = singleLine(cause.message);
    // Escaped as a GitHub Actions annotation, so npm's output cannot carry another workflow command.
    error(env.GITHUB_ACTIONS === 'true' ? `::error::${message.replace(/%/g, '%25')}` : message);
    return 1;
  }
}

function runCommand(command, args) {
  try {
    const stdout = execFileSync(command, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      // npm is a .cmd script on Windows, which only runs through a shell. Its arguments are validated.
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

function isEntryPoint(moduleUrl) {
  if (!process.argv[1]) return false;
  try {
    return realpathSync(process.argv[1]) === realpathSync(fileURLToPath(moduleUrl));
  } catch {
    return false;
  }
}

if (isEntryPoint(import.meta.url)) {
  process.exitCode = main({
    argv: process.argv.slice(2),
    run: runCommand,
    readFile: (path) => readFileSync(path),
    env: process.env,
    log: console.log,
    error: console.error,
  });
}
