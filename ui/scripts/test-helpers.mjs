// Simulated npm and git for the release script tests (node --test). Each command the script runs must
// be declared in the scenario; an undeclared command fails the test, so a script can never reach the real
// npm registry, the real Git remote or a publish by accident.

import { gzipSync } from 'node:zlib';

/** Result of a simulated command: exit status, stdout and stderr. */
export function result(status, stdout = '', stderr = '') {
  return { status, stdout, stderr };
}

/** JSON body that npm prints with --json when the registry answers 404. */
export function npmError(code, summary = 'simulated error') {
  return result(1, JSON.stringify({ error: { code, summary } }, null, 2), `npm error code ${code}`);
}

/**
 * Builds a fake `run(command, args)` from a map of "command arg1 arg2" → result (or function returning one).
 * Records every call in `calls`.
 */
export function fakeRun(responses) {
  const calls = [];
  const run = (command, args) => {
    const key = [command, ...args].join(' ');
    calls.push(key);
    if (!(key in responses)) {
      throw new Error(`Comando no simulado en la prueba: ${key}`);
    }
    const response = responses[key];
    return typeof response === 'function' ? response() : response;
  };
  return { run, calls };
}

/** Builds a fake `readFile(path)` from a map of path → content. A missing path throws like fs (ENOENT). */
export function fakeFiles(files) {
  return (path) => {
    if (!(path in files)) {
      const error = new Error(`ENOENT: no such file or directory, open '${path}'`);
      error.code = 'ENOENT';
      throw error;
    }
    return files[path];
  };
}

/** Builds an npm-like .tgz (gzip of a ustar archive) from path → text content (object or entry list). */
export function makeTarball(files) {
  const blocks = [];
  for (const [path, content] of Array.isArray(files) ? files : Object.entries(files)) {
    const body = Buffer.from(content, 'utf8');
    const header = Buffer.alloc(512);
    header.write(path, 0, 100, 'utf8');
    header.write('0000644\0', 100);
    header.write('0000000\0', 108);
    header.write('0000000\0', 116);
    header.write(`${body.length.toString(8).padStart(11, '0')}\0`, 124);
    header.write('00000000000\0', 136);
    header.write('        ', 148);
    header.write('0', 156);
    header.write('ustar\0', 257);
    header.write('00', 263);
    const checksum = header.reduce((sum, byte) => sum + byte, 0);
    header.write(`${checksum.toString(8).padStart(6, '0')}\0 `, 148);
    blocks.push(header, body, Buffer.alloc((512 - (body.length % 512)) % 512));
  }
  blocks.push(Buffer.alloc(1024));
  return gzipSync(Buffer.concat(blocks));
}
