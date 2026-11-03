# Identidad: Keycloak

Configuración de Keycloak del backend (ADR 0024): el *realm* `liv` como código (tarea T07 de la feature 001), el tema `liv` de las páginas de acceso y la imagen de Keycloak que lo incluye (tarea T08), y los resultados del *spike* (`spike/RESULTADOS.md`).

## Tema `liv` e imagen

`theme/liv/login/` tiene las plantillas FreeMarker de las páginas de acceso, hechas a partir de los *frames* "001 · Acceso" de LIV‑Diseño. `theme/liv/styles/liv.css` es su hoja de estilos: Tailwind con los *tokens* del paquete `liv-components`, con versión exacta en `theme/package.json`. Por la excepción del ADR 0024, el tema reproduce los componentes de la librería (Logo, Alert, TextField, IconButton, Button y Spinner) y `Keycloak/BrandPanel`, que solo existe en el tema. Los colores, las tipografías y las sombras salen solo de los *tokens*.

`Dockerfile` compila la hoja y construye Keycloak 26.8.1 con el tema y optimizado para PostgreSQL. El *realm* lo activa con `loginTheme: liv`, y el entorno local (`dev/compose.yaml`) usa esta imagen.

Desde `backend/`, en Windows desde Git Bash:

```bash
sh identity/theme/check.sh
```

La prueba levanta un entorno desechable, con proyecto y puertos propios, y lo borra al terminar. Comprueba que la página de inicio de sesión use el tema, esté en español y solo use *tokens* (001-19). Comprueba también que la contraseña incorrecta, el usuario inexistente y el usuario desactivado vean el mismo mensaje genérico (001-03, 001-04, 001-05). La prueba visual contra los *frames* y la de accesibilidad con axe van en las e2e (tarea T23).

## *Realm* como código

`config/base/liv-realm.yaml` es la configuración del *realm* para **todos** los entornos: roles, flujo con TOTP por rol, flujo de contraseña directa denegado, bloqueo, política de contraseñas, tiempos de sesión, eventos, perfil de usuario y clientes. `identity/config/` contiene solo ese archivo: es lo que se publica.

La capa de desarrollo, con usuarios ficticios, está en `backend/dev/keycloak/liv-realm-dev.yaml`, fuera de `identity/config/` para que ninguna imagen ni despliegue la copie.

Los aplica [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli) en ese orden; cada archivo solo cambia las secciones que declara. Reaplicar es seguro: deja el *realm* igual a los archivos, y es la forma de recuperarse si una aplicación falló a mitad.

`RealmConfigTest` (en `src/test/java/.../identityconfig/`) lee los archivos y comprueba las decisiones de la feature 001. Corre con `./mvnw verify`. `backend/dev/check.sh` comprueba además el *realm* ya aplicado en el entorno local.

### Valores fijos y variables

Los controles de seguridad son literales en la base y ningún entorno puede cambiarlos con una variable: HTTPS siempre (`sslRequired: all`; la capa dev lo relaja a `external` para usar `http://localhost`), bloqueo de 15 minutos tras 5 intentos y sin rotación del *refresh token*. Si las pruebas e2e necesitan otros valores, los cambian con su propia capa en `infra/`.

Los tiempos de sesión sí son variables para que las e2e puedan acortarlos; su valor por defecto es el de producción. Lo propio de cada entorno no tiene valor por defecto y, si falta, la aplicación falla.

| Variable | Por defecto | Uso |
|---|---|---|
| `LIV_BFF_BASE_URL` | — | URL pública del BFF, sin barra final: redirección del inicio de sesión |
| `LIV_BFF_BACKCHANNEL_URL` | — | URL del BFF en la red interna, sin barra final: cierre por canal trasero (`/bff/backchannel-logout` no se publica en internet) |
| `LIV_BFF_CLIENT_SECRET` | — | Secreto del cliente `liv-bff` |
| `LIV_BFF_SERVICE_CLIENT_SECRET` | — | Secreto de `liv-bff-service` (llamadas internas del BFF, audiencia `liv-internal`) |
| `LIV_AUDIT_IMPORTER_SECRET` | — | Secreto de `liv-audit-importer` (lee los eventos para la auditoría de LIV) |
| `LIV_KC_ACCESS_TOKEN_SECONDS` | `300` | Vida del *token* de acceso (001-10) |
| `LIV_KC_SSO_IDLE_SECONDS` | `1500` | Inactividad de la sesión; debe ser ≥ inactividad del BFF + vida del *token* + margen (001-10) |
| `LIV_KC_SSO_MAX_SECONDS` | `29400` | Duración máxima de la sesión (001-10) |

## Limitaciones conocidas

- keycloak-config-cli 6.5.1 está compilado para Keycloak 26.5.5. Con Keycloak 26.8.1 se verificó en T07 que aplica y reaplica sin errores y que `check.sh` pasa sobre el *realm* aplicado; al subir cualquiera de los dos, repite esa verificación.
- keycloak-config-cli no puede reaplicar credenciales TOTP sin duplicarlas. Por eso la capa dev no trae TOTP: los usuarios con rol de MFA lo configuran en su primer ingreso. Los usuarios con TOTP conocido para las pruebas e2e se crean en `infra/` (tarea T23).
- Que un código TOTP equivocado cuente para el bloqueo (001-04, 001-07) es comportamiento de Keycloak que la configuración no puede probar; lo demuestran las e2e de T23.
- Además del bloqueo de 001-04, Keycloak bloquea 60 segundos si dos intentos fallidos llegan con menos de 1 segundo de diferencia (protección contra intentos automáticos).
