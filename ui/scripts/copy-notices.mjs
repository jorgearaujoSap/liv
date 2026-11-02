// Copia THIRD_PARTY_NOTICES.md al paquete: ng-packagr no permite tomar assets fuera de la carpeta de la
// librería y la licencia ISC exige acompañar el material de Lucide en cada copia.
import { copyFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
copyFileSync(
  join(root, 'THIRD_PARTY_NOTICES.md'),
  join(root, 'dist', 'liv-components', 'THIRD_PARTY_NOTICES.md'),
);
