// Extracts the section of one version from CHANGELOG.md into a file, for the GitHub release notes
// (feature 002, ui-release.yml, job `verificar`). The notes travel as a file, never interpolated in `run:`.
//
// Usage: node scripts/release-notes.mjs <X.Y.Z> <output file>
import { readFileSync, writeFileSync } from 'node:fs';

import { EXACT_VERSION, isEntryPoint } from './release-decide.mjs';

/** Body of the "## [version]" section (without its heading), up to the next "## " heading. */
export function extractSection(changelog, version) {
  if (!EXACT_VERSION.test(version)) {
    throw new Error(`La versión "${version}" no es X.Y.Z.`);
  }
  const lines = changelog.split(/\r?\n/);
  const start = lines.findIndex((line) => line.startsWith(`## [${version}]`));
  if (start === -1) {
    throw new Error(
      `CHANGELOG.md no tiene la sección "## [${version}]". Describe ahí qué cambia en esta versión.`,
    );
  }
  const next = lines.findIndex((line, index) => index > start && line.startsWith('## '));
  const body = lines
    .slice(start + 1, next === -1 ? undefined : next)
    .join('\n')
    .trim();
  if (!body) {
    throw new Error(`La sección "## [${version}]" de CHANGELOG.md está vacía.`);
  }
  return `${body}\n`;
}

export function main({ argv, readFile, writeFile, log, error }) {
  if (argv.length !== 2) {
    error('Uso: node scripts/release-notes.mjs <X.Y.Z> <archivo de salida>');
    return 1;
  }
  const [version, output] = argv;
  try {
    writeFile(output, extractSection(readFile('CHANGELOG.md'), version));
  } catch (cause) {
    error(`No se generan las notas de ${version}: ${cause.message}`);
    return 1;
  }
  log(`Notas de la versión ${version} escritas en ${output}.`);
  return 0;
}

if (isEntryPoint(import.meta.url)) {
  process.exitCode = main({
    argv: process.argv.slice(2),
    readFile: (path) => readFileSync(path, 'utf8'),
    writeFile: (path, content) => writeFileSync(path, content, 'utf8'),
    log: console.log,
    error: console.error,
  });
}
