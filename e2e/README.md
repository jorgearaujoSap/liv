# liv-e2e

Pruebas de navegador de LIV con Playwright: recorren las pantallas como una persona y comprueban la funcionalidad y la interfaz de cada feature. Por decisión del responsable (ADR 0030), hasta antes de producción corren **en local, contra el entorno de desarrollo**, sin imágenes, `Dockerfile` ni CI. Usan el sistema solo por HTTP.

## Requisitos

- Node 22.12 o superior.
- El sistema levantado como dice `README.md` de la raíz › «Ejecutar LIV en local»: frontend en `http://localhost:4200` y Keycloak en `http://localhost:8180`.

## Comandos

Desde `e2e/`:

| Comando | Qué hace |
|---|---|
| `npm install` y `npm run navegador` | La primera vez: instala las dependencias y el Chromium de Playwright |
| `npm test` | Corre todas las pruebas |
| `npm run informe` | Abre el informe de la última corrida, con capturas, videos y trazas de lo que falló |
| `npm run test:actualizar-capturas` | Vuelve a generar las capturas de referencia. Solo después de un cambio visual aprobado por el responsable |

Si tu entorno local usa otras direcciones o credenciales de administración de Keycloak, expórtalas antes (las de `backend/dev/.env`): `LIV_E2E_BASE_URL`, `LIV_E2E_KEYCLOAK_URL`, `KEYCLOAK_ADMIN_USER` y `KEYCLOAK_ADMIN_PASSWORD`.

## Qué prueban

| Archivo | Criterios |
|---|---|
| `pruebas/acceso.spec.ts` | Vuelta a la ruta pedida (001-01), página de acceso en español y con axe (001-19), «Mostrar contraseña», mensaje único (001-03), usuario desactivado (001-05), rol sin segundo factor (001-08), código TOTP incorrecto y correcto (001-07), configurar el segundo factor en el primer ingreso (001-06) y bloqueo tras 5 fallos (001-04) |
| `pruebas/sesion.spec.ts` | Ningún *token* en el navegador y la cookie `LIV_SESSION`, `HttpOnly`, `Secure` y `SameSite=Strict` (001-11); salir sin *tokens* en la URL, «atrás» sin datos y otra ruta que vuelve a pedir entrar (001-09); rechazo anti-CSRF (001-12) |
| `pruebas/sesion-corta.spec.ts` | Aviso de inactividad con «Seguir conectado» y «Cerrar sesión» (001-22) y vencimiento sin actividad (001-10). Solo con la sesión corta (abajo) |
| `pruebas/estructura.spec.ts` | Cabecera con nombre, administración y rol, y tarjetas iguales (001-02), menú por rol con el tercer nivel y la opción 1.4 ya abierta (001-15), «Sin permiso» por URL (001-16) y «Próximamente» (con la opción 1.7), con axe en cada pantalla |
| `pruebas/vigencias.spec.ts` | Feature 007, opción 1.4 · Vigencias fiscales: «Sin permiso» del `LIQUIDADOR` (007-02), estado vacío con el rango de años (007-03), el diálogo con la lista desplegable de años del servidor, con el año en curso elegido y las advertencias (007-22, 007-04), crear años en cualquier orden y con huecos (007-06, V‑02 ya no rechaza), rechazos V‑05 por la API (007-07), rechazos V‑01 (007-05) y V‑06 por concurrencia, abrir la preparación y la lista con «Habilitar liquidación» inhabilitado (007-10 a 007-12, V‑03), `CONSULTA` y `CARGADOR_TABLAS` sin acciones, también por la API (007-01, 007-09), aislamiento con `e2e.admin.b` (CA‑12, 007-18, 007-19), «Crear vigencia» inhabilitado cuando ya existen todas (007-23), teclado del diálogo y de los botones inhabilitados con motivo, y axe con contraste y capturas de las pantallas (007-21) |

Lo que todavía no cubren, y queda en otras pruebas:
- que un código TOTP inválido cuente para el bloqueo (001-07): lo prueba `backend/identity/theme/check.sh`;
- el 403 de la API en una opción sin permiso (001-16): la feature 001 aún no tiene una API con permiso propio; lo prueban las del backend.

## Vigencias de la feature 007 (comprobación previa)

Las pruebas de `pruebas/vigencias.spec.ts` crean vigencias y la API no las borra (decisión H2‑9 de la feature 007). Por eso:

- Antes de la primera prueba de `vigencias.spec.ts` (un `beforeAll`), se mira por la API, con `dev.consulta` y `dev.consulta.b` (sin TOTP), si el Municipio de Ipiales o la administración de prueba B ya tienen vigencias. Si las tienen, se detienen solo las pruebas de la 1.4, con un mensaje que manda a limpiarlas: las de la 001 corren igual.
- Quien corre las pruebas las limpia antes con `sh dev/limpiar-vigencias.sh`, desde `backend/` (lo agrega el PR B10 de la feature 007). `e2e/` nunca lo llama: no toca la base de datos, solo usa el sistema por HTTP (ADR 0030).
- Los años no salen del calendario de la prueba sino de `creatableYears` de la API: se toman una sola vez, de la lista de una administración vacía, por su relación con el año en curso (`CURRENT`, `NEXT`, `PAST`) y su posición (`aniosDePrueba`). Las capturas enmascaran los años y las fechas. Las tablas muestran las fechas como `dd/mm/aaaa` y el diálogo y el detalle, en fecha larga («1 de enero de 2026»).
- Si tu entorno local usa otros puertos (por ejemplo, el frontend en `4300` porque el `4200` lo usa otro proyecto), exporta `LIV_E2E_BASE_URL` antes de correr las pruebas.
- Los rechazos V‑05 (007-07) no se pueden provocar desde la pantalla, que solo ofrece los años que la API acepta: se prueban por la API, sin crear nada. V‑02 ya no rechaza (enmienda 4): 007-06 crea años en cualquier orden y con huecos.
- La prueba 007-23 va al final de la serie de la administración B: crea por la API todos los años que le faltan (de 2002 al año siguiente, unos 26) y la deja sin años que crear. `limpiar-vigencias.sh` borra todo antes de la corrida siguiente.
- En una segunda corrida sin limpiar, las pruebas de la 1.4 fallan a propósito en ese `beforeAll`: es lo esperado.

## Aviso de inactividad (sesión corta)

Con los 15 minutos reales, `pruebas/sesion-corta.spec.ts` tardaría más de un cuarto de hora, así que se omite salvo que se pida: `npm test` la muestra como «skipped» (omitida), no como aprobada. Para correrla:

1. Levanta el BFF con una sesión de 150 segundos: en el paso 3 de «Ejecutar LIV en local», `SPRING_SESSION_TIMEOUT=150s ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
2. Desde `e2e/`: `LIV_E2E_SESION_CORTA=1 npx playwright test pruebas/sesion-corta.spec.ts` (unos 4 minutos).
3. Vuelve a levantar el BFF sin la variable para el uso normal.

## Usuarios

- Usan los usuarios ficticios de `backend/dev/keycloak/liv-realm-dev.yaml` (contraseña `Liv-Dev-2026!`).
- Al empezar (`preparar.ts`), las pruebas crean dos usuarios propios con la API de administración de Keycloak, y los borran al terminar (`limpiar.ts`):
  - `e2e.totp`: administrador con segundo factor ya configurado. Su secreto TOTP es ficticio y lo conocen las pruebas, que calculan el mismo código de 6 dígitos que daría una aplicación de autenticación (`support/totp.ts`). Keycloak lo valida como siempre.
  - `e2e.bloqueo`: se bloquea a propósito en la prueba de 001-04.
  - `e2e.alta`: administrador sin segundo factor, que lo configura en la prueba de 001-06 con la clave que muestra la página, como haría una persona.
  - `e2e.admin.b`: `ADMIN_SISTEMA` de la «Administración de prueba B», con otro secreto TOTP ficticio y conocido (`TOTP_SECRETO_B`). Lo usan las pruebas de aislamiento de la 1.4 (CA‑12). No va en el *realm* de desarrollo: `ADMIN_SISTEMA` exige TOTP y la capa de desarrollo no puede llevar uno conocido.
- No van en la capa de desarrollo del *realm*, porque `keycloak-config-cli` duplicaría el TOTP al reaplicarla. Los usuarios de desarrollo y el segundo factor no cambian.
- `e2e.totp` y `e2e.admin.b` entran con segundo factor **una sola vez** por archivo de pruebas y guardan su sesión en `resultados/` (`guardarSesionConSegundoFactor`): cada entrada con TOTP puede esperar hasta 30 s por un código nuevo.
- Si una corrida se interrumpe (Ctrl+C), los usuarios quedan hasta la siguiente, que los borra y crea de nuevo. Para borrarlos antes, en la consola de administración de Keycloak (`http://localhost:8180/admin`, *realm* `liv` › *Users*) busca `e2e.` y bórralos.

## Capturas de referencia

- Están en `pruebas/*.spec.ts-snapshots/` y las aprueba el responsable.
- El nombre lleva el navegador y el sistema operativo (`-chromium-win32.png`): en otro sistema operativo, la primera corrida genera las suyas y hay que revisarlas igual.
- Una prueba falla si la pantalla cambia más del 1 %. El informe muestra la diferencia.

## Cómo se usa en cada feature

La *skill* `liv-sdd-feature` (paso 7, ADR 0030) lo pide en cada feature con interfaz:
- la feature agrega aquí sus pruebas, primero en rojo, en un PR propio de `e2e/`;
- al validar, el agente levanta el sistema, corre `npm test` y revisa cada pantalla contra Figma;
- el resultado va en el PR del frontend.

Cómo se escribe cada prueba (nombres, sesión, localizadores, axe y capturas): la *skill* `liv-prueba-e2e`, en `e2e/.claude/skills/liv-prueba-e2e/SKILL.md`. Léela antes de escribir una prueba, aunque tu agente no la cargue solo.
