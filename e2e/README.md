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

## Qué prueban (feature 001)

| Archivo | Criterios |
|---|---|
| `pruebas/acceso.spec.ts` | Vuelta a la ruta pedida (001-01), página de acceso en español y con axe (001-19), «Mostrar contraseña», mensaje único (001-03), usuario desactivado (001-05), rol sin segundo factor (001-08), código TOTP incorrecto y correcto (001-07), configurar el segundo factor en el primer ingreso (001-06) y bloqueo tras 5 fallos (001-04) |
| `pruebas/sesion.spec.ts` | Ningún *token* en el navegador y la cookie `LIV_SESSION`, `HttpOnly`, `Secure` y `SameSite=Strict` (001-11); salir sin *tokens* en la URL, «atrás» sin datos y otra ruta que vuelve a pedir entrar (001-09); rechazo anti-CSRF (001-12) |
| `pruebas/estructura.spec.ts` | Cabecera con nombre, administración y rol, y tarjetas iguales (001-02), menú por rol con el tercer nivel (001-15), «Sin permiso» por URL (001-16) y «Próximamente», con axe en cada pantalla |

Lo que todavía no cubren, y queda en otras pruebas:
- la expiración por inactividad y el aviso de los 2 minutos (001-10 y 001-22): necesitan una sesión del BFF corta;
- que un código TOTP inválido cuente para el bloqueo (001-07): lo prueba `backend/identity/theme/check.sh`;
- el 403 de la API en una opción sin permiso (001-16): la feature 001 aún no tiene una API con permiso propio; lo prueban las del backend.

## Usuarios

- Usan los usuarios ficticios de `backend/dev/keycloak/liv-realm-dev.yaml` (contraseña `Liv-Dev-2026!`).
- Al empezar (`preparar.ts`), las pruebas crean dos usuarios propios con la API de administración de Keycloak, y los borran al terminar (`limpiar.ts`):
  - `e2e.totp`: administrador con segundo factor ya configurado. Su secreto TOTP es ficticio y lo conocen las pruebas, que calculan el mismo código de 6 dígitos que daría una aplicación de autenticación (`support/totp.ts`). Keycloak lo valida como siempre.
  - `e2e.bloqueo`: se bloquea a propósito en la prueba de 001-04.
  - `e2e.alta`: administrador sin segundo factor, que lo configura en la prueba de 001-06 con la clave que muestra la página, como haría una persona.
- No van en la capa de desarrollo del *realm*, porque `keycloak-config-cli` duplicaría el TOTP al reaplicarla. Los usuarios de desarrollo y el segundo factor no cambian.
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
