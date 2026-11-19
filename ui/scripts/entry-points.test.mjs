// Each release script, run as a process, must end in 1 when its check fails: never in 0 without doing
// anything (feature 002, 002-04 and 002-10). They run from an empty directory, where every check fails,
// both directly and through a junction (a symbolic link on Linux) to the scripts directory.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

const SCRIPTS = dirname(fileURLToPath(import.meta.url));
const CASES = {
  'check-release.mjs': ['--ci'],
  'release-decide.mjs': [],
  'release-notes.mjs': [],
  'release-publish.mjs': [],
};

function exitCode(script, args) {
  try {
    execFileSync(process.execPath, [script, ...args], {
      cwd: mkdtempSync(join(tmpdir(), 'release-entry-cwd-')),
      stdio: 'pipe',
      env: { ...process.env, GITHUB_OUTPUT: '' },
    });
    return 0;
  } catch (error) {
    return error.status;
  }
}

const link = join(mkdtempSync(join(tmpdir(), 'release-entry-link-')), 'scripts');
symlinkSync(SCRIPTS, link, 'junction');

describe('scripts de publicación · como proceso', () => {
  for (const [script, args] of Object.entries(CASES)) {
    it(`${script} termina en 1 cuando falla, ejecutado directamente`, () => {
      assert.equal(exitCode(join(SCRIPTS, script), args), 1);
    });

    it(`${script} termina en 1 cuando falla, ejecutado por un enlace al directorio`, () => {
      assert.equal(exitCode(join(link, script), args), 1);
    });
  }
});
