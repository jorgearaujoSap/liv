// Publishes the verified .tgz of liv-components without publishing twice (feature 002, 002-06 and 002-08).
// Runs in the `publicar` job of ui-release.yml, which has id-token: write, no checkout and no installed
// dependencies: this file travels alone as an artifact of `decidir`, so it imports nothing but Node.
//
//   - the version already exists with the same integrity → it continues without publishing (a re-run);
//   - it exists with another integrity → it fails: the published content is not this .tgz;
//   - npm answers that it does not exist (E404) → npm publish <tgz> --ignore-scripts --access public;
//   - any other answer from npm → it fails.
//
// Usage: node release-publish.mjs <file.tgz> <name> <X.Y.Z> [--dry-run]
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const LIBRARY_NAME = 'liv-components';
const EXACT_VERSION = /^\d+\.\d+\.\d+$/;

class PublishError extends Error {}

function fail(message) {
  throw new PublishError(message);
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
  if (!tarball.endsWith('.tgz') || tarball.startsWith('-')) {
    fail(`"${tarball}" no es un archivo .tgz.`);
  }
  return { tarball, name, version, dryRun };
}

/** Subresource integrity of the tarball, as npm stores it in dist.integrity. */
export function integrityOf(content) {
  return `sha512-${createHash('sha512').update(content).digest('base64')}`;
}

function npmErrorCode(stdout) {
  try {
    return JSON.parse(stdout)?.error?.code ?? null;
  } catch {
    return null;
  }
}

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
      `npm no respondió si ${name}@${version} existe (${view.stderr.trim() || `código ${view.status}`}). No se publica: vuelve a ejecutar cuando npm responda.`,
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
      `npm respondió sin la integridad de ${name}@${version}: "${view.stdout.trim()}". No se publica.`,
    );
  }
  return integrity;
}

/** Returns the message of what it did; throws PublishError if it must fail. */
export function publish({ argv, run, readFile }) {
  const { tarball, name, version, dryRun } = parseArgs(argv);
  let local;
  try {
    local = integrityOf(readFile(tarball));
  } catch (cause) {
    fail(`No se pudo leer ${tarball}: ${cause.message}`);
  }

  const published = publishedIntegrity(run, name, version);
  if (published === local) {
    return `${name}@${version} ya está publicada con la misma integridad: no se publica otra vez.`;
  }
  if (published !== null) {
    fail(
      `${name}@${version} ya está publicada con otra integridad (${published}); este paquete tiene ${local}. No se publica: el contenido publicado no es el que se verificó.`,
    );
  }

  const args = ['publish', tarball, '--ignore-scripts', '--access', 'public'];
  if (dryRun) args.push('--dry-run');
  const outcome = run('npm', args);
  if (outcome.status !== 0) {
    fail(`npm publish falló (código ${outcome.status}): ${outcome.stderr.trim()}`);
  }
  return dryRun
    ? `DRY-RUN: no publica. npm publish --dry-run de ${name}@${version} (${local}) terminó bien.`
    : `Publicada ${name}@${version} (${local}).`;
}

export function main({ argv, run, readFile, env, log, error }) {
  try {
    log(publish({ argv, run, readFile }));
    return 0;
  } catch (cause) {
    if (!(cause instanceof PublishError)) throw cause;
    error(`${env.GITHUB_ACTIONS === 'true' ? '::error::' : ''}${cause.message}`);
    return 1;
  }
}

function runCommand(command, args) {
  try {
    const stdout = execFileSync(command, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      // npm is a .cmd script on Windows, which only runs through a shell. Its arguments are validated.
      shell: process.platform === 'win32',
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
    readFile: (path) => readFileSync(path),
    env: process.env,
    log: console.log,
    error: console.error,
  });
}
