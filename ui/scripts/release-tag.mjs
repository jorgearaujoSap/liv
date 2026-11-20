// Creates the tag ui-vX.Y.Z on the commit of this run and its GitHub release, without publishing twice and
// without moving a tag (feature 002, 002-03 and 002-06). Runs in the `etiquetar` job of ui-release.yml, which
// has contents: write, no checkout and no installed dependencies: this file travels alone as an artifact of
// `decidir`, so it imports nothing but Node. It talks to GitHub with `gh`, which reads GH_TOKEN.
//
//   - the tag is created by the GitHub API on the commit of the run, not from a local checkout;
//   - the tag already exists on that same commit → it continues (a re-run);
//   - the tag exists on another commit → it fails and never moves it;
//   - the release is created with --verify-tag and --notes-file; if it already exists, it continues;
//   - any other answer from GitHub (not "not found") → it fails.
//
// Usage: GITHUB_REPOSITORY=<owner/repo> node release-tag.mjs <name> <X.Y.Z> <commit> <notes file>
import { execFileSync } from 'node:child_process';
import { readFileSync, realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const LIBRARY_NAME = 'liv-components';
const EXACT_VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const COMMIT_SHA = /^[0-9a-f]{40}$/;
const REPOSITORY = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
// A plain relative or absolute path: no shell syntax, no option-like start.
const NOTES_PATH = /^(?!-)[\w./\\:-]+$/;
// A tag can point to another tag; real ones have one level. More than this is not a normal tag.
const MAX_TAG_DEPTH = 5;

// Where the person finds how to resolve each failure (feature 002, 002-04).
const GUIDE = 'Guía: ui/README.md › «Publicar una versión» › «Si falla «etiquetar»».';

class TagError extends Error {}

function fail(message) {
  throw new TagError(message);
}

function singleLine(value) {
  return String(value).replace(/[\r\n]+/g, ' ');
}

export function parseArgs(argv, env) {
  if (argv.length !== 4) {
    fail(
      'Uso: GITHUB_REPOSITORY=<owner/repo> node release-tag.mjs <nombre> <X.Y.Z> <commit> <notas>',
    );
  }
  const [name, version, commit, notes] = argv;
  const repository = env.GITHUB_REPOSITORY ?? '';
  if (name !== LIBRARY_NAME) fail(`Solo se etiqueta ${LIBRARY_NAME}, no "${name}".`);
  if (!EXACT_VERSION.test(version)) fail(`La versión "${version}" no es X.Y.Z.`);
  if (!COMMIT_SHA.test(commit)) fail(`"${commit}" no es un commit de 40 caracteres hexadecimales.`);
  if (!REPOSITORY.test(repository) || repository.split('/').some((part) => /^\.+$/.test(part))) {
    fail(`GITHUB_REPOSITORY ("${repository}") no es <dueño>/<repositorio>.`);
  }
  if (!NOTES_PATH.test(notes)) fail(`"${notes}" no es la ruta de un archivo de notas.`);
  return { name, version, commit, repository, notes, tag: `ui-v${version}` };
}

function failure(result) {
  return singleLine(result.stderr.trim() || result.stdout.trim() || `código ${result.status}`);
}

/** `gh` answers "HTTP 404" in stderr when the resource does not exist. */
function isNotFound(result) {
  return result.status !== 0 && /HTTP 404/.test(result.stderr);
}

function parseJson(text, what) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return fail(`GitHub respondió algo ilegible sobre ${what}.`);
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return fail(`GitHub respondió algo ilegible sobre ${what}.`);
  }
  return parsed;
}

/**
 * Commit that the tag points to (following annotated tags), or null if the tag does not exist.
 * Anything other than a clear "not found" is an error: never "it does not exist" by guessing.
 */
function tagCommit({ run, repository, tag }) {
  const ref = run('gh', ['api', `repos/${repository}/git/ref/tags/${tag}`]);
  if (isNotFound(ref)) return null;
  if (ref.status !== 0) fail(`GitHub no respondió si existe la etiqueta ${tag} (${failure(ref)}).`);
  let object = parseJson(ref.stdout, `la etiqueta ${tag}`).object;
  for (let depth = 0; depth < MAX_TAG_DEPTH; depth++) {
    if (object?.type === 'commit' && COMMIT_SHA.test(object.sha ?? '')) return object.sha;
    if (object?.type !== 'tag' || !COMMIT_SHA.test(object.sha ?? '')) break;
    const annotated = run('gh', ['api', `repos/${repository}/git/tags/${object.sha}`]);
    if (annotated.status !== 0) {
      fail(`No se pudo leer la etiqueta anotada ${tag} (${failure(annotated)}).`);
    }
    object = parseJson(annotated.stdout, `la etiqueta anotada ${tag}`).object;
  }
  return fail(`La etiqueta ${tag} no apunta a un commit que se pueda leer.`);
}

function foreignTag(tag, found, commit) {
  return `La etiqueta ${tag} ya existe en el commit ${found}, no en el de esta ejecución (${commit}). No se mueve.`;
}

/** Creates the tag on `commit`, or confirms that it is already there. Returns what it did. */
function ensureTag({ run, repository, tag, commit }) {
  const existing = tagCommit({ run, repository, tag });
  if (existing !== null) {
    if (existing !== commit) fail(foreignTag(tag, existing, commit));
    return `La etiqueta ${tag} ya existe en el commit de esta ejecución.`;
  }
  const created = run('gh', [
    'api',
    '--method',
    'POST',
    `repos/${repository}/git/refs`,
    '-f',
    `ref=refs/tags/${tag}`,
    '-f',
    `sha=${commit}`,
  ]);
  if (created.status === 0) return `Etiqueta ${tag} creada en ${commit}.`;
  // Two runs at once: the other one created it between the read and the create. Read it again.
  const raced = tagCommit({ run, repository, tag });
  if (raced === null) fail(`No se pudo crear la etiqueta ${tag} (${failure(created)}).`);
  if (raced !== commit) fail(foreignTag(tag, raced, commit));
  return `La etiqueta ${tag} ya existe en el commit de esta ejecución.`;
}

function readRelease({ run, repository, tag }) {
  const release = run('gh', ['api', `repos/${repository}/releases/tags/${tag}`]);
  if (isNotFound(release)) return null;
  if (release.status !== 0) {
    fail(`GitHub no respondió si existe la release ${tag} (${failure(release)}).`);
  }
  return parseJson(release.stdout, `la release ${tag}`);
}

/** What it says of a release that already exists: it never changes it, but it says if its text differs. */
function existingRelease(release, tag, content) {
  const same = String(release.body ?? '').trim() === content.trim();
  return same
    ? `La release ${tag} ya existe.`
    : `La release ${tag} ya existe, con un texto distinto de la sección del CHANGELOG.md. No se cambia: revísala.`;
}

/** Creates the release of the tag with the notes, or confirms that it exists. Returns what it did. */
function ensureRelease({ run, repository, tag, name, version, notes, content }) {
  const existing = readRelease({ run, repository, tag });
  if (existing !== null) return existingRelease(existing, tag, content);
  const created = run('gh', [
    'release',
    'create',
    tag,
    '--repo',
    repository,
    '--verify-tag',
    // The tags of the three projects share this repository: this release must not become its "Latest".
    '--latest=false',
    '--notes-file',
    notes,
    '--title',
    `${name} ${version}`,
  ]);
  if (created.status === 0) return `Release ${tag} creada.`;
  // Two runs at once: the other one created it between the read and the create. Read it again.
  const raced = readRelease({ run, repository, tag });
  if (raced !== null) return existingRelease(raced, tag, content);
  return fail(`No se pudo crear la release ${tag} (${failure(created)}).`);
}

/** Returns the messages of what it did; throws TagError if it must fail. */
export function tag({ argv, env, run, readFile }) {
  const args = parseArgs(argv, env);
  let content;
  try {
    content = readFile(args.notes);
  } catch (cause) {
    fail(`No se pudo leer ${args.notes}: ${singleLine(cause.message)}. No se etiqueta.`);
  }
  if (!content.trim()) fail(`Las notas ${args.notes} están vacías. No se etiqueta.`);
  return [ensureTag({ run, ...args }), ensureRelease({ run, ...args, content })];
}

export function main({ argv, env, run, readFile, log, error }) {
  try {
    for (const message of tag({ argv, env, run, readFile })) log(message);
    return 0;
  } catch (cause) {
    if (!(cause instanceof TagError)) throw cause;
    const message = singleLine(`${cause.message} ${GUIDE}`);
    // Escaped as a GitHub Actions annotation, so GitHub's output cannot carry another workflow command.
    error(env.GITHUB_ACTIONS === 'true' ? `::error::${message.replace(/%/g, '%25')}` : message);
    return 1;
  }
}

function runCommand(command, args) {
  try {
    const stdout = execFileSync(command, args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
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
    env: process.env,
    run: runCommand,
    readFile: (path) => readFileSync(path, 'utf8'),
    log: console.log,
    error: console.error,
  });
}
