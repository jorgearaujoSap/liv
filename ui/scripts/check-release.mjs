// Pre-release checks for liv-components. npm runs this automatically before `npm run release`
// ("prerelease" script). Each failed check explains how to fix it; nothing is published until all pass.
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const LIBRARY_DIR = 'projects/liv-components';
const RELEASE_BRANCH = 'main';

const { name, version } = JSON.parse(readFileSync(`${LIBRARY_DIR}/package.json`, 'utf8'));
const problems = [];

function run(command) {
  try {
    return execSync(command, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

function compareVersions(left, right) {
  const parse = (value) => value.split('-')[0].split('.').map(Number);
  const [a, b] = [parse(left), parse(right)];
  for (let i = 0; i < 3; i++) {
    if (a[i] !== b[i]) return a[i] - b[i];
  }
  return 0;
}

if (!/^\d+\.\d+\.\d+$/.test(version)) {
  problems.push(
    `La versión "${version}" no es X.Y.Z. Usa npm run version:patch, version:minor o version:major.`,
  );
}

const branch = run('git rev-parse --abbrev-ref HEAD');
if (branch !== RELEASE_BRANCH) {
  problems.push(
    `Estás en la rama "${branch}". Solo se publica desde ${RELEASE_BRANCH}, después de integrar el PR: git checkout ${RELEASE_BRANCH} && git pull.`,
  );
}

const pendingChanges = run('git status --porcelain -- .');
if (pendingChanges) {
  problems.push('Hay cambios sin commit en ui/. Publica solo lo que ya está integrado en main.');
}

const user = run('npm whoami');
if (!user) {
  problems.push('No has iniciado sesión en npm. Ejecuta npm login y repite.');
}

const published = run(`npm view ${name} versions --json`);
const publishedVersions = published ? [].concat(JSON.parse(published)) : [];
if (publishedVersions.includes(version)) {
  problems.push(
    `La versión ${version} ya está publicada y no se puede reemplazar. Sube la versión (npm run version:patch|minor|major), anótala en CHANGELOG.md e intégrala por PR.`,
  );
}

const latest = run(`npm view ${name} dist-tags.latest`);
if (latest && compareVersions(version, latest) <= 0) {
  problems.push(`La versión ${version} no es mayor que la publicada como latest (${latest}).`);
}

if (user && publishedVersions.length > 0) {
  const owners = run(`npm owner ls ${name}`) ?? '';
  if (!owners.split(/\r?\n/).some((line) => line.split(' ')[0] === user)) {
    problems.push(
      `Tu usuario de npm (${user}) no puede publicar ${name}. Pide a un responsable actual que ejecute: npm owner add ${user} ${name}`,
    );
  }
}

if (!readFileSync('CHANGELOG.md', 'utf8').includes(`## [${version}]`)) {
  problems.push(
    `CHANGELOG.md no tiene la sección "## [${version}]". Describe ahí qué cambia en esta versión.`,
  );
}

if (problems.length > 0) {
  console.error(`\nNo se publica ${name}@${version}:\n`);
  problems.forEach((problem, index) => console.error(`  ${index + 1}. ${problem}`));
  console.error('\nProceso completo: ui/README.md, sección "Publicar una versión".\n');
  process.exit(1);
}

console.log(`Verificaciones correctas: se publicará ${name}@${version} como ${user}.`);
