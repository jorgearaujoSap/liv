// Tests of the release notes extraction (feature 002, 002-03 and 002-04).
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { extractSection, main } from './release-notes.mjs';
import { fakeFiles } from './test-helpers.mjs';

const CHANGELOG = [
  '# Changelog — liv-components',
  '',
  '## [Sin publicar]',
  '',
  '- algo sin publicar',
  '',
  '## [0.6.0] — 2026-11-20',
  '',
  'Resumen de la 0.6.0.',
  '',
  '### Agregado',
  '',
  '- `Tabs`.',
  '',
  '## [0.5.0] — 2026-11-16',
  '',
  '- `Select`.',
  '',
].join('\r\n');

describe('release-notes · sección del CHANGELOG.md', () => {
  it('extrae solo la sección de la versión, sin su título y sin la siguiente', () => {
    assert.equal(
      extractSection(CHANGELOG, '0.6.0'),
      'Resumen de la 0.6.0.\n\n### Agregado\n\n- `Tabs`.\n',
    );
  });

  it('extrae la última sección del archivo', () => {
    assert.equal(extractSection(CHANGELOG, '0.5.0'), '- `Select`.\n');
  });

  it('no confunde 0.6.0 con 0.6.01 ni con [Sin publicar]', () => {
    assert.throws(
      () => extractSection(CHANGELOG, '0.6.01'),
      /no tiene la sección "## \[0\.6\.01\]"/,
    );
  });

  it('falla si falta la sección (002-04)', () => {
    assert.throws(() => extractSection(CHANGELOG, '0.7.0'), /no tiene la sección "## \[0\.7\.0\]"/);
  });

  it('falla si la sección está vacía', () => {
    assert.throws(
      () => extractSection('## [0.7.0] — 2026-11-21\n\n## [0.6.0]\n\n- x\n', '0.7.0'),
      /está vacía/,
    );
  });

  it('falla si la versión no es X.Y.Z', () => {
    assert.throws(() => extractSection(CHANGELOG, '0.6.0\n## [0.5.0]'), /no es X\.Y\.Z/);
  });
});

describe('release-notes · línea de comandos', () => {
  function runMain(argv) {
    const written = {};
    const errors = [];
    const code = main({
      argv,
      readFile: fakeFiles({ 'CHANGELOG.md': CHANGELOG }),
      writeFile: (path, content) => (written[path] = content),
      log: () => {},
      error: (line) => errors.push(line),
    });
    return { code, written, errors };
  }

  it('escribe las notas en el archivo pedido', () => {
    const { code, written } = runMain(['0.6.0', 'notas.md']);
    assert.equal(code, 0);
    assert.match(written['notas.md'], /^Resumen de la 0\.6\.0\./);
  });

  it('termina en 1 sin escribir nada si falta la sección', () => {
    const { code, written, errors } = runMain(['0.7.0', 'notas.md']);
    assert.equal(code, 1);
    assert.deepEqual(written, {});
    assert.match(errors.join('\n'), /no tiene la sección/);
  });

  it('termina en 1 con un número de argumentos distinto de dos', () => {
    assert.equal(runMain(['0.6.0']).code, 1);
  });
});
