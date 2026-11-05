// Adopta los contratos OpenAPI publicados por el backend (ADR 0027, regla IN-04).
//
//   npm run api:update    descarga los contratos fijados en api-spec/versions.json, verifica su
//                         SHA-256, escribe api-spec/<nombre>.yaml y regenera el cliente
//   npm run api:generate  regenera el cliente de src/app/api/ desde lo que hay en api-spec/
//   npm run api:verify    comprueba, sin red, que api-spec/ coincide con versions.json
//
// Los contratos salen de las releases públicas `contract-<nombre>-vX.Y.Z` del repositorio, sin
// token. Este script nunca lee `../backend`: el único contrato entre los dos proyectos es lo
// publicado.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_SPEC_DIR = join(projectDir, 'api-spec');
const DEFAULT_API_DIR = join(projectDir, 'src', 'app', 'api');

// Propietario y nombre empiezan por un carácter alfanumérico: así no admiten «.» ni «..».
const REPOSITORY = /^[A-Za-z0-9][A-Za-z0-9_.-]*\/[A-Za-z0-9][A-Za-z0-9_.-]*$/;
const NAME = /^[a-z0-9-]+$/;
const VERSION = /^\d+\.\d+\.\d+$/;
const SHA256 = /^[0-9a-f]{64}$/;

const DOWNLOAD_TIMEOUT_MS = 30_000;
const MAX_CONTRACT_BYTES = 2 * 1024 * 1024;

export const sha256Hex = (content) => createHash('sha256').update(content).digest('hex');

export const contractUrl = (repository, name, version) =>
  `https://github.com/${repository}/releases/download/contract-${name}-v${version}/${name}.yaml`;

/** Lee y valida `versions.json`: nada de lo que dice llega a una URL o a una ruta sin pasar por aquí. */
export function readVersions(specDir = DEFAULT_SPEC_DIR) {
  const file = join(specDir, 'versions.json');
  const fail = (reason) => {
    throw new Error(`api-spec/versions.json: ${reason}`);
  };
  const versions = JSON.parse(readFileSync(file, 'utf8'));
  if (!REPOSITORY.test(versions.repository ?? '')) {
    fail('repository debe ser «propietario/nombre»');
  }
  const entries = Object.entries(versions.contracts ?? {});
  if (entries.length === 0) fail('no lista ningún contrato');
  for (const [name, entry] of entries) {
    const { version, sha256 } = entry ?? {};
    if (!NAME.test(name))
      fail(`el nombre de contrato «${name}» solo admite minúsculas, dígitos y guiones`);
    if (!VERSION.test(version)) fail(`${name}: la versión debe ser X.Y.Z`);
    if (!SHA256.test(sha256))
      fail(`${name}: sha256 debe ser hexadecimal en minúsculas de 64 caracteres`);
  }
  return versions;
}

/** Descarga cada contrato fijado y comprueba su SHA-256. Devuelve nombre → contenido. */
export async function fetchContracts(versions, fetchFn = fetch) {
  const contracts = new Map();
  for (const [name, { version, sha256 }] of Object.entries(versions.contracts)) {
    const url = contractUrl(versions.repository, name, version);
    const response = await fetchFn(url, { signal: AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS) });
    if (!response.ok) {
      throw new Error(`${name} ${version}: la descarga de ${url} respondió ${response.status}`);
    }
    const content = Buffer.from(await response.arrayBuffer());
    if (content.length > MAX_CONTRACT_BYTES) {
      throw new Error(`${name} ${version}: la respuesta supera los 2 MiB; no es un contrato`);
    }
    const actual = sha256Hex(content);
    if (actual !== sha256) {
      throw new Error(
        `${name} ${version}: el SHA-256 descargado (${actual}) no es el fijado en versions.json (${sha256})`,
      );
    }
    contracts.set(name, content);
  }
  return contracts;
}

/** Nombres de los contratos (`*.yaml`) que hay en `specDir`. */
const yamlFiles = (specDir) =>
  existsSync(specDir) ? readdirSync(specDir).filter((file) => file.endsWith('.yaml')) : [];

/** Problemas de `api-spec/` frente a `versions.json`, sin red. Vacío si todo coincide. */
export function verifyLocal(specDir, versions) {
  const problems = [];
  for (const [name, { sha256 }] of Object.entries(versions.contracts)) {
    const file = join(specDir, `${name}.yaml`);
    if (!existsSync(file)) {
      problems.push(`${name}.yaml: falta; ejecuta npm run api:update`);
    } else if (sha256Hex(readFileSync(file)) !== sha256) {
      problems.push(`${name}.yaml: su SHA-256 no es el fijado en versions.json`);
    }
  }
  for (const file of yamlFiles(specDir)) {
    if (!Object.hasOwn(versions.contracts, file.replace(/\.yaml$/, ''))) {
      problems.push(`${file}: no está en versions.json`);
    }
  }
  return problems;
}

/** Descarga y verifica todo primero; solo entonces toca `api-spec/`. */
export async function updateApiSpec({ specDir = DEFAULT_SPEC_DIR, fetchFn = fetch } = {}) {
  const versions = readVersions(specDir);
  const contracts = await fetchContracts(versions, fetchFn);
  // Cada copia se escribe con nombre temporal y se renombra sobre la anterior, que se reemplaza en
  // un solo paso; solo después se borran las de contratos que ya no están. Si algo falla, no quedan
  // temporales.
  try {
    for (const [name, content] of contracts) {
      writeFileSync(join(specDir, `${name}.yaml.tmp`), content);
    }
    for (const name of contracts.keys()) {
      renameSync(join(specDir, `${name}.yaml.tmp`), join(specDir, `${name}.yaml`));
    }
  } finally {
    for (const name of contracts.keys()) rmSync(join(specDir, `${name}.yaml.tmp`), { force: true });
  }
  for (const file of yamlFiles(specDir)) {
    if (!contracts.has(file.replace(/\.yaml$/, ''))) rmSync(join(specDir, file));
  }
  return versions;
}

const pascal = (name) =>
  name
    .split('-')
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join('');

/**
 * Genera el cliente de cada contrato en `<outDir>/<nombre>/` con ng-openapi-gen. Cada contrato
 * lleva clases con nombre propio (CoreApi, BffApiConfiguration…) para que los tres convivan.
 */
export function generateClient({ specDir = DEFAULT_SPEC_DIR, outDir = DEFAULT_API_DIR, names }) {
  const require = createRequire(import.meta.url);
  const generator = require.resolve('ng-openapi-gen/lib/index.js');
  for (const name of names) {
    // Se genera aparte y solo si termina bien reemplaza al cliente anterior.
    const out = join(outDir, name);
    const staging = join(outDir, `.${name}.tmp`);
    rmSync(staging, { recursive: true, force: true });
    mkdirSync(staging, { recursive: true });
    try {
      execFileSync(
        process.execPath,
        [
          generator,
          '--silent',
          'true',
          '--input',
          join(specDir, `${name}.yaml`),
          '--output',
          staging,
          '--endOfLineStyle',
          'lf',
          '--apiService',
          `${pascal(name)}Api`,
          '--configuration',
          `${pascal(name)}ApiConfiguration`,
        ],
        { stdio: ['ignore', 'inherit', 'inherit'] },
      );
      rmSync(out, { recursive: true, force: true });
      renameSync(staging, out);
    } finally {
      rmSync(staging, { recursive: true, force: true });
    }
  }
}

async function main(args) {
  const versions = readVersions();
  const names = Object.keys(versions.contracts);
  if (args.includes('--verify')) {
    const problems = verifyLocal(DEFAULT_SPEC_DIR, versions);
    problems.forEach((problem) => console.error(`api-spec/${problem}`));
    if (problems.length > 0) process.exitCode = 1;
    return;
  }
  if (!args.includes('--generate-only')) {
    await updateApiSpec();
    console.log(`api-spec/: ${names.join(', ')} descargados y verificados.`);
  }
  generateClient({ names });
  console.log(`src/app/api/: cliente generado para ${names.join(', ')}.`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main(process.argv.slice(2)).catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
