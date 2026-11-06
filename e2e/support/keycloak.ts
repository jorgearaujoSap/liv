import {
  CLAVE,
  IPIALES,
  KEYCLOAK_ADMIN_PASSWORD,
  KEYCLOAK_ADMIN_USER,
  KEYCLOAK_URL,
  TOTP_SECRETO,
  USUARIOS_E2E,
} from './entorno';

/** Lo mínimo de la API de administración de Keycloak para preparar y limpiar los usuarios de las pruebas. */

async function tokenAdmin(): Promise<string> {
  const respuesta = await fetch(`${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: 'admin-cli',
      username: KEYCLOAK_ADMIN_USER,
      password: KEYCLOAK_ADMIN_PASSWORD,
    }),
  });
  if (!respuesta.ok) {
    throw new Error(
      `No se pudo entrar a la administración de Keycloak (${respuesta.status}). ¿Está levantado el entorno local? ` +
        'Si usa otras credenciales, exporta KEYCLOAK_ADMIN_USER y KEYCLOAK_ADMIN_PASSWORD (las de backend/dev/.env).',
    );
  }
  return ((await respuesta.json()) as { access_token: string }).access_token;
}

async function admin(token: string, metodo: string, ruta: string, cuerpo?: unknown): Promise<Response> {
  return fetch(`${KEYCLOAK_URL}/admin/realms/liv${ruta}`, {
    method: metodo,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
}

async function idDe(token: string, usuario: string): Promise<string | undefined> {
  const respuesta = await admin(token, 'GET', `/users?username=${encodeURIComponent(usuario)}&exact=true`);
  const encontrados = (await respuesta.json()) as { id: string }[];
  return encontrados[0]?.id;
}

async function borrar(token: string, usuario: string): Promise<void> {
  const id = await idDe(token, usuario);
  if (id) {
    await admin(token, 'DELETE', `/attack-detection/brute-force/users/${id}`);
    await admin(token, 'DELETE', `/users/${id}`);
  }
}

function usuario(nombre: string, nombres: string, credenciales: unknown[]) {
  return {
    username: nombre,
    enabled: true,
    email: `${nombre}@example.test`,
    emailVerified: true,
    firstName: nombres,
    lastName: 'Prueba E2E',
    attributes: { administration_id: [IPIALES] },
    credentials: credenciales,
  };
}

async function crear(token: string, cuerpo: ReturnType<typeof usuario>, rol: string): Promise<void> {
  const creado = await admin(token, 'POST', '/users', cuerpo);
  if (creado.status !== 201) {
    throw new Error(`No se pudo crear ${cuerpo.username} en Keycloak (${creado.status}): ${await creado.text()}`);
  }
  const id = await idDe(token, cuerpo.username);
  const respuestaRol = await admin(token, 'GET', `/roles/${rol}`);
  if (!respuestaRol.ok) {
    throw new Error(`No existe el rol ${rol} en el realm liv (${respuestaRol.status})`);
  }
  const rolRep = await respuestaRol.json();
  const asignado = await admin(token, 'POST', `/users/${id}/role-mappings/realm`, [rolRep]);
  if (!asignado.ok) {
    throw new Error(`No se pudo dar el rol ${rol} a ${cuerpo.username} (${asignado.status})`);
  }
}

const clave = { type: 'password', value: CLAVE, temporary: false };

/**
 * Crea de nuevo los usuarios de las pruebas: si quedaron de una corrida anterior, los borra antes. Si algo falla, los
 * borra todos, porque Playwright no corre `limpiar.ts` cuando falla `preparar.ts`.
 */
export async function prepararUsuarios(): Promise<void> {
  const token = await tokenAdmin();
  await limpiarConToken(token);
  try {
    await crearUsuarios(token);
  } catch (error) {
    await limpiarConToken(token);
    throw error;
  }
}

async function crearUsuarios(token: string): Promise<void> {
  await crear(
    token,
    usuario(USUARIOS_E2E.totp, 'Tomás', [
      clave,
      {
        type: 'otp',
        userLabel: 'e2e',
        secretData: JSON.stringify({ value: TOTP_SECRETO }),
        credentialData: JSON.stringify({
          subType: 'totp',
          digits: 6,
          counter: 0,
          period: 30,
          algorithm: 'HmacSHA1',
        }),
      },
    ]),
    'ADMIN_SISTEMA',
  );
  await crear(token, usuario(USUARIOS_E2E.bloqueo, 'Berta', [clave]), 'CONSULTA');
  await crear(token, usuario(USUARIOS_E2E.alta, 'Ana', [clave]), 'ADMIN_SISTEMA');
}

async function limpiarConToken(token: string): Promise<void> {
  for (const nombre of Object.values(USUARIOS_E2E)) {
    await borrar(token, nombre);
  }
}

/** Borra los usuarios de las pruebas y su bloqueo, si lo hay. */
export async function limpiarUsuarios(): Promise<void> {
  await limpiarConToken(await tokenAdmin());
}
