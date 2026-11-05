// Pruebas de scripts/api-update.mjs (T20, feature 001, ADR 0027). Se ejecutan con `node --test`,
// sin red: la descarga se simula con un `fetch` falso.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, relative } from 'node:path';
import { afterEach, beforeEach, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  contractUrl,
  fetchContracts,
  generateClient,
  readVersions,
  updateApiSpec,
  verifyLocal,
} from './api-update.mjs';

const projectDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const sha256 = (text) => createHash('sha256').update(text).digest('hex');

const CONTENT = {
  core: 'openapi: 3.0.3\ninfo:\n  title: core\n  version: 1.0.0\n',
  bff: 'openapi: 3.0.3\ninfo:\n  title: bff\n  version: 2.1.0\n',
};

const versionsFile = (overrides = {}) => ({
  repository: 'owner/repo',
  contracts: {
    core: { version: '1.0.0', sha256: sha256(CONTENT.core) },
    bff: { version: '2.1.0', sha256: sha256(CONTENT.bff) },
  },
  ...overrides,
});

// Un `fetch` falso que responde por URL y recuerda qué se pidió.
const fakeFetch = (responses) => {
  const calls = [];
  const fetchFn = async (url) => {
    calls.push(url);
    const body = responses[url];
    return body === undefined
      ? new Response('no encontrado', { status: 404 })
      : new Response(body, { status: 200 });
  };
  fetchFn.calls = calls;
  return fetchFn;
};

const published = {
  'https://github.com/owner/repo/releases/download/contract-core-v1.0.0/core.yaml': CONTENT.core,
  'https://github.com/owner/repo/releases/download/contract-bff-v2.1.0/bff.yaml': CONTENT.bff,
};

let dir;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'liv-api-spec-'));
});
afterEach(() => rmSync(dir, { recursive: true, force: true }));

const writeVersions = (value) => writeFileSync(join(dir, 'versions.json'), JSON.stringify(value));

describe('contractUrl', () => {
  it('apunta a la release contract-<nombre>-vX.Y.Z, sin token', () => {
    assert.equal(
      contractUrl('jorgearaujoSap/liv', 'core', '1.0.0'),
      'https://github.com/jorgearaujoSap/liv/releases/download/contract-core-v1.0.0/core.yaml',
    );
  });
});

describe('readVersions', () => {
  it('lee la versión y el SHA-256 de cada contrato', () => {
    writeVersions(versionsFile());
    assert.deepEqual(readVersions(dir), versionsFile());
  });

  const invalid = {
    'un nombre con ruta': { contracts: { '../core': { version: '1.0.0', sha256: sha256('x') } } },
    'una versión que no es X.Y.Z': { contracts: { core: { version: '1.0', sha256: sha256('x') } } },
    'un SHA-256 que no es hexadecimal de 64 caracteres': {
      contracts: { core: { version: '1.0.0', sha256: 'abc' } },
    },
    'sin contratos': { contracts: {} },
  };
  for (const [caso, overrides] of Object.entries(invalid)) {
    it(`rechaza ${caso}`, () => {
      writeVersions(versionsFile(overrides));
      assert.throws(() => readVersions(dir), /versions\.json/);
    });
  }

  it('rechaza un repositorio que no es propietario/nombre', () => {
    writeVersions(versionsFile({ repository: 'https://evil.example/x' }));
    assert.throws(() => readVersions(dir), /repository/);
  });
});

describe('fetchContracts', () => {
  it('descarga cada contrato de su release y devuelve el contenido verificado', async () => {
    const fetchFn = fakeFetch(published);

    const contracts = await fetchContracts(versionsFile(), fetchFn);

    assert.equal(contracts.get('core').toString(), CONTENT.core);
    assert.equal(contracts.get('bff').toString(), CONTENT.bff);
    assert.deepEqual(fetchFn.calls.sort(), Object.keys(published).sort());
  });

  it('falla si el SHA-256 de lo descargado no es el fijado', async () => {
    const tampered = {
      ...published,
      'https://github.com/owner/repo/releases/download/contract-core-v1.0.0/core.yaml':
        CONTENT.core + '# cambiado\n',
    };

    await assert.rejects(fetchContracts(versionsFile(), fakeFetch(tampered)), /core.*SHA-256/s);
  });

  it('falla si la release no existe', async () => {
    await assert.rejects(fetchContracts(versionsFile(), fakeFetch({})), /core.*404/s);
  });
});

describe('updateApiSpec', () => {
  it('escribe cada contrato verificado en api-spec/<nombre>.yaml', async () => {
    writeVersions(versionsFile());

    await updateApiSpec({ specDir: dir, fetchFn: fakeFetch(published) });

    assert.equal(readFileSync(join(dir, 'core.yaml'), 'utf8'), CONTENT.core);
    assert.equal(readFileSync(join(dir, 'bff.yaml'), 'utf8'), CONTENT.bff);
  });

  it('no escribe ningún archivo si un solo contrato no verifica', async () => {
    writeVersions(versionsFile());
    const tampered = {
      ...published,
      'https://github.com/owner/repo/releases/download/contract-bff-v2.1.0/bff.yaml': 'otro\n',
    };

    await assert.rejects(updateApiSpec({ specDir: dir, fetchFn: fakeFetch(tampered) }), /bff/);

    assert.equal(existsSync(join(dir, 'core.yaml')), false);
    assert.equal(existsSync(join(dir, 'bff.yaml')), false);
  });

  it('borra de api-spec/ un contrato que versions.json ya no lista', async () => {
    writeVersions(versionsFile());
    writeFileSync(join(dir, 'viejo.yaml'), 'openapi: 3.0.3\n');

    await updateApiSpec({ specDir: dir, fetchFn: fakeFetch(published) });

    assert.equal(existsSync(join(dir, 'viejo.yaml')), false);
  });
});

describe('verifyLocal', () => {
  it('no encuentra problemas cuando las copias coinciden con versions.json', () => {
    writeFileSync(join(dir, 'core.yaml'), CONTENT.core);
    writeFileSync(join(dir, 'bff.yaml'), CONTENT.bff);

    assert.deepEqual(verifyLocal(dir, versionsFile()), []);
  });

  it('detecta una copia editada a mano, una que falta y una que sobra', () => {
    writeFileSync(join(dir, 'core.yaml'), CONTENT.core + 'editado\n');
    writeFileSync(join(dir, 'extra.yaml'), 'openapi: 3.0.3\n');

    const problems = verifyLocal(dir, versionsFile());

    assert.equal(problems.length, 3);
    assert.match(problems.join('\n'), /core\.yaml.*SHA-256/s);
    assert.match(problems.join('\n'), /bff\.yaml.*falta/s);
    assert.match(problems.join('\n'), /extra\.yaml.*versions\.json/s);
  });
});

describe('api-spec/ y src/app/api/ del proyecto', () => {
  const specDir = join(projectDir, 'api-spec');
  const versions = readVersions(specDir);

  it('versions.json fija core, platform y bff, y no core-internal (ADR 0025)', () => {
    assert.deepEqual(Object.keys(versions.contracts).sort(), ['bff', 'core', 'platform']);
  });

  it('las copias de api-spec/ coinciden con el SHA-256 de versions.json', () => {
    assert.deepEqual(verifyLocal(specDir, versions), []);
  });

  it('el cliente de src/app/api/ es el que se genera de esas copias', () => {
    const out = join(dir, 'api');
    generateClient({ specDir, outDir: out, names: Object.keys(versions.contracts) });

    for (const name of Object.keys(versions.contracts)) {
      const generated = listFiles(join(out, name));
      const committed = listFiles(join(projectDir, 'src', 'app', 'api', name));
      assert.deepEqual(committed, generated, `${name}: lista de archivos`);
      for (const file of generated) {
        assert.equal(
          readFileSync(join(projectDir, 'src', 'app', 'api', name, file), 'utf8'),
          readFileSync(join(out, name, file), 'utf8'),
          `${name}/${file}`,
        );
      }
    }
  });
});

function listFiles(root) {
  const files = [];
  const walk = (current) => {
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      const path = join(current, entry.name);
      if (entry.isDirectory()) walk(path);
      else files.push(relative(root, path).replaceAll('\\', '/'));
    }
  };
  walk(root);
  return files.sort();
}
