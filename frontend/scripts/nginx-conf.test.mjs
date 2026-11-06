// Pruebas de nginx.conf y del Dockerfile del frontend (T21, feature 001; doc. 02 §8 y doc. 03 §4.1):
// cabeceras de seguridad, `Cache-Control: no-store` en index.html, nada de la API servido por el
// frontend e imágenes fijadas por digest. Son estáticas: la verificación en un contenedor real
// (nginx -t y las cabeceras de las respuestas) está en frontend/README.md.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

const projectDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (file) => readFileSync(join(projectDir, file), 'utf8').replace(/\r\n/g, '\n');
const withoutComments = (text) => text.replace(/^\s*#.*$/gm, '');

const conf = withoutComments(read('nginx.conf'));

/** Recorre las directivas de nginx y las devuelve con la pila de bloques que las contiene. */
function directives(text) {
  const result = [];
  const stack = [];
  let current = '';
  let quote = '';
  for (const char of text) {
    if (quote) {
      current += char;
      if (char === quote) {
        quote = '';
      }
    } else if (char === '"' || char === "'") {
      quote = char;
      current += char;
    } else if (char === '{') {
      stack.push(current.trim().replace(/\s+/g, ' '));
      current = '';
    } else if (char === '}') {
      stack.pop();
      current = '';
    } else if (char === ';') {
      result.push({ text: current.trim().replace(/\s+/g, ' '), path: [...stack] });
      current = '';
    } else {
      current += char;
    }
  }
  return result;
}

const all = directives(conf);
const headers = all.filter((d) => d.text.startsWith('add_header '));
const header = (name) => headers.find((d) => d.text.split(' ')[1].toLowerCase() === name.toLowerCase());
const headerValue = (name) => {
  const found = header(name);
  assert.ok(found, `falta la cabecera ${name}`);
  const match = found.text.match(/^add_header \S+ "([^"]*)"|^add_header \S+ (\S+)/);
  return match[1] ?? match[2];
};

describe('nginx.conf: cabeceras de seguridad (doc. 02 §8)', () => {
  it('todas se declaran una sola vez, en el bloque server y con `always`', () => {
    assert.ok(headers.length > 0);
    for (const d of headers) {
      assert.deepEqual(
        d.path.filter((block) => block !== 'server'),
        [],
        `«${d.text}» está dentro de un location o un if: nginx descartaría las cabeceras del server`,
      );
      assert.match(d.text, / always$/, `«${d.text}» sin always`);
    }
    const names = headers.map((d) => d.text.split(' ')[1].toLowerCase());
    assert.equal(new Set(names).size, names.length, 'cabecera repetida');
  });

  it('HSTS por un año como mínimo', () => {
    const value = headerValue('Strict-Transport-Security');
    assert.ok(Number(value.match(/max-age=(\d+)/)[1]) >= 31_536_000);
  });

  it('X-Content-Type-Options: nosniff y Referrer-Policy', () => {
    assert.equal(headerValue('X-Content-Type-Options'), 'nosniff');
    assert.equal(headerValue('Referrer-Policy'), 'no-referrer');
  });

  describe('Content-Security-Policy', () => {
    const csp = Object.fromEntries(
      headerValue('Content-Security-Policy')
        .split(';')
        .map((directive) => directive.trim().split(/\s+/))
        .filter(([name]) => name)
        .map(([name, ...sources]) => [name, sources]),
    );

    it('nada se permite salvo lo que se declara', () => {
      assert.deepEqual(csp['default-src'], ["'none'"]);
    });

    it('no se puede enmarcar en otro sitio', () => {
      assert.deepEqual(csp['frame-ancestors'], ["'none'"]);
    });

    it('los scripts son solo los del propio sitio: sin unsafe-inline ni unsafe-eval', () => {
      assert.deepEqual(csp['script-src'], ["'self'"]);
    });

    it('el único manejador en línea es el de la hoja de estilos que genera Angular, por su hash', () => {
      const hash = createHash('sha256').update("this.media='all'").digest('base64');
      assert.deepEqual(csp['script-src-attr'], ["'unsafe-hashes'", `'sha256-${hash}'`]);
    });

    it('las peticiones salen solo hacia el propio sitio (el frontend y el BFF comparten origen)', () => {
      assert.deepEqual(csp['connect-src'], ["'self'"]);
      assert.deepEqual(csp['form-action'], ["'self'"]);
      assert.deepEqual(csp['base-uri'], ["'self'"]);
    });

    it('sin plugins ni objetos incrustados', () => {
      assert.deepEqual(csp['object-src'], ["'none'"]);
    });

    it('imágenes y fuentes propias; las imágenes pueden ser data: (iconos en línea)', () => {
      assert.deepEqual(csp['img-src'], ["'self'", 'data:']);
      assert.deepEqual(csp['font-src'], ["'self'"]);
    });

    it('los estilos admiten unsafe-inline: Angular crea <style> al ejecutarse (decisión en el PR)', () => {
      assert.deepEqual(csp['style-src'], ["'self'", "'unsafe-inline'"]);
    });
  });
});

/** La tabla de caché mira el estado de la respuesta además de la ruta: `"$status:$uri"`. */
const mapEntries = () => all.filter((d) => d.path.at(-1) === 'map "$status:$uri" $cache_control');
const hashedPattern = () => {
  const entry = mapEntries().find((d) => d.text.endsWith('immutable"'));
  return new RegExp(entry.text.match(/^"~([^"]+)"/)[1]);
};

describe('nginx.conf: caché', () => {
  it('Cache-Control sale de una tabla: no-store salvo los archivos con hash en el nombre', () => {
    assert.equal(headerValue('Cache-Control'), '$cache_control');
    const map = mapEntries().map((d) => d.text);
    assert.ok(map.length > 0, 'falta el map de Cache-Control');
    assert.ok(map.includes('default "no-store"'));
    assert.ok(map.some((entry) => entry.endsWith('"public, max-age=31536000, immutable"')));
  });

  it('el patrón del archivo con hash coincide con los que genera Angular y no con index.html', () => {
    const regex = hashedPattern();
    assert.ok(regex.test('200:/main-ON6NZC24.js'));
    assert.ok(regex.test('200:/styles-ELR7ATZQ.css'));
    assert.ok(!regex.test('200:/index.html'));
    assert.ok(!regex.test('200:/'));
    assert.ok(!regex.test('200:/favicon.ico'));
  });

  it('un archivo con hash que no existe (404) no se guarda un año: dejaría la pantalla en blanco', () => {
    assert.ok(!hashedPattern().test('404:/chunk-ON6NZC24.js'));
  });
});

describe('nginx.conf: qué sirve', () => {
  const server = all.filter((d) => d.path.at(-1) === 'server');

  it('escucha en el puerto 8080, sin privilegios, y no anuncia su versión', () => {
    assert.ok(server.some((d) => d.text === 'listen 8080'));
    assert.ok(server.some((d) => d.text === 'server_tokens off'));
  });

  it('la API y el BFF no son del frontend: responden 404 y no caen en index.html', () => {
    const block = all.find((d) =>
      d.path.at(-1)?.startsWith('location ~ ^/(api|bff|internal|login/oauth2)(/|$)'),
    );
    assert.ok(block, 'falta el location de /api, /bff, /internal y /login/oauth2');
    assert.equal(block.text, 'return 404');
  });

  it('el registro de accesos no guarda la consulta de la URL: puede llevar datos (doc. 02 §6)', () => {
    const format = all.find((d) => d.text.startsWith('log_format '));
    assert.ok(format, 'falta el log_format propio');
    assert.doesNotMatch(
      format.text,
      /\$request\b|\$request_uri|\$args|\$query_string|\$http_referer/,
    );
    assert.match(format.text, /\$uri/);
    const log = all.find((d) => d.text.startsWith('access_log ') && !d.text.includes(' off'));
    assert.ok(log?.text.endsWith(` ${format.text.split(' ')[1]}`), 'access_log sin el formato propio');
  });

  it('un archivo con extensión que no existe es 404; una ruta de la aplicación cae en index.html', () => {
    assert.ok(all.some((d) => d.path.at(-1)?.startsWith('location ~*') && d.text === 'try_files $uri =404'));
    assert.ok(all.some((d) => d.path.at(-1) === 'location /' && d.text === 'try_files $uri /index.html'));
  });

  it('solo GET y HEAD', () => {
    assert.ok(all.some((d) => d.path.at(-1)?.startsWith('if ($request_method') && d.text === 'return 405'));
  });

  it('tiene una ruta de salud para el orquestador', () => {
    assert.ok(all.some((d) => d.path.at(-1) === 'location = /healthz' && d.text.startsWith('return 200')));
  });
});

describe('Dockerfile', () => {
  const dockerfile = read('Dockerfile');
  const froms = dockerfile.split('\n').filter((line) => line.startsWith('FROM '));

  it('cada imagen base está fijada por digest (doc. 03 §4.1)', () => {
    assert.ok(froms.length >= 2);
    for (const line of froms) {
      assert.match(line, /@sha256:[0-9a-f]{64}/, line);
    }
  });

  it('compila con Node y sirve con nginx sin privilegios; Node no llega a la imagen final', () => {
    assert.match(froms[0], /node:/);
    assert.match(froms.at(-1), /nginxinc\/nginx-unprivileged:/);
    assert.doesNotMatch(dockerfile.split(froms.at(-1))[1], /\bnode\b|npm /);
  });

  it('instala con npm ci y copia nginx.conf y la compilación a la imagen final', () => {
    assert.match(dockerfile, /npm ci/);
    assert.match(dockerfile, /COPY nginx\.conf \/etc\/nginx\/conf\.d\/default\.conf/);
    assert.match(dockerfile, /COPY --from=\w+ \S*dist\/liv-frontend\/browser \/usr\/share\/nginx\/html/);
  });

  it('corre con el usuario 101, explícito, y nunca vuelve a root', () => {
    assert.doesNotMatch(dockerfile, /^USER\s+(root|0)\b/m);
    assert.match(dockerfile.split(froms.at(-1))[1], /^USER 101$/m);
  });

  it('lleva el aviso de licencias de las dependencias que Angular junta al compilar', () => {
    assert.match(
      dockerfile,
      /COPY --from=\w+ \S*dist\/liv-frontend\/3rdpartylicenses\.txt \S+/,
    );
  });

  it('.dockerignore deja fuera los secretos y la salida de pruebas', () => {
    const ignore = read('.dockerignore').split('\n');
    for (const pattern of ['.env*', '*.pem', '*.key', 'coverage', '.npmrc']) {
      assert.ok(ignore.includes(pattern), `.dockerignore sin ${pattern}`);
    }
  });
});
