# 001 · Inicio de sesión

**Estado:** aprobada (H1) — 2026-11-01, responsable funcional · enmienda 1 (001-04, 001-08, 001-11, 001-18) decidida por el responsable funcional el 2026-11-01 tras la revisión adversarial del plan · enmienda 2 (001-04, 001-19) decidida el mismo día tras el *spike* de Keycloak (T06)
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `platform` (seguridad, contexto de inquilino, auditoría) y `core` (usuarios), BFF, frontend y librería · **Menú:** transversal (acceso al sistema y menú principal) · **Tipo de cambio:** crítico (autenticación y autorización, `AGENTS.md` §4.3)
**Fuentes:** `docs/arquitectura/02` §3 (identidad), §4 (autorización y roles), §5 (aislamiento), §9 (auditoría); `docs/arquitectura/01` §2 (BFF), §10 (recurso de capacidades), §11 (frontend); ADR 0004 y 0010; Doc. 01 §3–§4 (menú y roles) y Doc. 04 §1 y §10 (menú del Módulo 4 y roles de recaudo)

> Primera feature del proyecto y **guía de referencia** para el equipo: recorre el flujo completo (spec, Figma, plan, tareas con TDD, revisiones y un PR por proyecto).

## Qué hace

Permite que un funcionario de una administración tributaria entre al sistema con su usuario y contraseña, y con un segundo factor si su rol lo exige, en una página de inicio de sesión con la imagen de LIV. Una vez dentro, ve su nombre, su administración y solo las opciones de menú que sus permisos le permiten. Puede cerrar sesión, y la sesión se cierra sola si deja de usarla.

## Por qué

Ninguna funcionalidad del sistema puede construirse sin saber quién es el usuario, a qué administración pertenece y qué puede hacer: la segregación de funciones, el aislamiento entre administraciones y la auditoría dependen de ello. Además, es la feature con la que el equipo aprende el método de trabajo antes de las features de negocio.

## Criterios de aceptación

_Cada criterio se comprueba con sí/no. No hay reglas funcionales con código para el inicio de sesión, así que se usan los códigos `001-xx`._

**Inicio de sesión**

- [ ] **001-01** — Dado un usuario sin sesión, cuando abre cualquier ruta de la aplicación, entonces es llevado a la página de inicio de sesión de LIV y, tras autenticarse, vuelve a la ruta que pidió. _Prueba:_ pendiente
- [ ] **001-02** — Dado un usuario activo con credenciales correctas y sin segundo factor obligatorio, cuando inicia sesión, entonces entra a la página de inicio y ve en la cabecera su nombre, su administración y su rol. _Prueba:_ pendiente
- [x] **001-03** — Dado un usuario, cuando ingresa una contraseña incorrecta o un usuario inexistente, entonces ve el mismo mensaje genérico en español, sin revelar si el usuario existe. _Prueba:_ `identity/theme/check.sh`, «Mensaje único de fallo»: misma página, mismo texto y mismo código HTTP (backend; PR #57). Se ejecuta a mano hasta que exista su trabajo de CI; T23 lo repite de punta a punta
- [x] **001-04** — Dado un usuario, cuando falla el inicio de sesión 5 veces seguidas, entonces su cuenta se bloquea durante 15 minutos. Todo fallo de inicio de sesión muestra el mismo mensaje: "Usuario o contraseña incorrectos. Tras varios intentos fallidos el acceso se bloquea por 15 minutos.", sin revelar si el usuario existe ni si está bloqueado. _Enmienda 2 (reemplaza la enmienda 1): Keycloak no distingue la cuenta bloqueada sin una extensión en Java; el responsable eligió el mensaje único y 001-03 vuelve a aplicar sin excepción._ _Prueba:_ `RealmConfigTest` (5 intentos y 15 minutos; PR #26) e `identity/theme/check.sh` (la cuenta bloqueada tras 5 fallos, con la contraseña correcta, ve la misma página, PR #57; 5 códigos inválidos bloquean la cuenta con el mismo mensaje, PR #58). `check.sh` se ejecuta a mano hasta que exista su trabajo de CI; el instante en que Keycloak aún no registra el quinto fallo es un riesgo aceptado (plan, «Riesgos»)
- [x] **001-05** — Dado un usuario desactivado, cuando intenta iniciar sesión con credenciales correctas, entonces no entra y ve el mensaje genérico. _Prueba:_ `identity/theme/check.sh`: no entra y recibe la misma página que con una contraseña incorrecta (backend; PR #57). Se ejecuta a mano hasta que exista su trabajo de CI

**Segundo factor (MFA)**

- [x] **001-06** — Dado un usuario con rol `ADMIN_SISTEMA`, `APROBADOR_TABLAS`, `APROBADOR_LIQUIDACION`, `APROBADOR_RECAUDO`, `TESORERIA`, `JURIDICO` o `AUDITOR`, cuando inicia sesión por primera vez, entonces debe configurar un segundo factor con una aplicación de autenticación (código de 6 dígitos) antes de entrar. _Prueba:_ `RealmConfigTest` (TOTP obligatorio para los 7 roles; PR #26) e `identity/theme/check.sh` (con `ADMIN_SISTEMA`: la página de configurar, el código inválido y la entrada con el código correcto; PR #58). `check.sh` se ejecuta a mano hasta que exista su trabajo de CI; T23 lo repite de punta a punta
- [x] **001-07** — Dado un usuario con segundo factor configurado, cuando inicia sesión, entonces debe ingresar el código; un código inválido no le permite entrar y cuenta como intento fallido. _Prueba:_ `identity/theme/check.sh` (el código inválido no entra; 5 códigos inválidos bloquean la cuenta; con el código correcto entra; PR #58). Se ejecuta a mano hasta que exista su trabajo de CI; T23 lo repite de punta a punta
- [ ] **001-08** — Dado un usuario cuyos roles no exigen segundo factor, cuando inicia sesión, entonces entra sin él. _Enmienda 1: configurarlo de forma voluntaria pasa a la feature posterior "Mi cuenta"._ _Prueba:_ pendiente

**Sesión**

- [ ] **001-09** — Dado un usuario con sesión, cuando pulsa "Salir", entonces la sesión termina en la aplicación y en el proveedor de identidad; volver atrás en el navegador no muestra datos y cualquier acción lo lleva a iniciar sesión. _Prueba:_ pendiente
- [ ] **001-10** — Dado un usuario sin actividad durante 15 minutos, cuando intenta una acción, entonces su sesión ha expirado y debe iniciar sesión de nuevo; ninguna sesión dura más de 8 horas aunque haya actividad. _Prueba:_ pendiente
- [ ] **001-11** — Dado cualquier momento de la sesión, entonces el navegador no recibe ni guarda ningún *token* (ni en cookies, ni en `localStorage`, ni en `sessionStorage`), y la única cookie de LIV que autentica es la de sesión, `HttpOnly`, `Secure` y `SameSite=Strict`. _Enmienda 1: la cookie anti‑CSRF de 001-12 no es una credencial; las cookies propias del proveedor de identidad están en su dominio._ _Prueba:_ pendiente
- [ ] **001-22** — Dado un usuario sin actividad, cuando faltan 2 minutos para que su sesión expire, entonces ve un aviso que le permite seguir conectado o cerrar sesión; si elige seguir, la sesión se extiende sin perder lo que estaba haciendo (WCAG 2.2.1). _Agregado al aprobar el diseño, 2026-11-01._ _Prueba:_ pendiente
- [ ] **001-12** — Dada una petición que modifica datos, cuando llega sin el *token* anti‑CSRF válido, entonces se rechaza. _Prueba:_ pendiente

**Identidad, permisos y aislamiento**

- [x] **001-13** — Dado un usuario con sesión, cuando la aplicación consulta quién es, entonces el backend responde su identificador, nombre, administración, roles y permisos, tomados del servidor y nunca de datos enviados por el navegador. _Prueba:_ `CurrentUserTest` (backend; PR #53)
- [ ] **001-14** — Dada una petición a la API sin *token* válido (ausente, vencido, de otra audiencia o con firma inválida), entonces responde 401 sin datos. _Prueba:_ pendiente
- [ ] **001-15** — Dado un usuario con sesión, cuando se carga la aplicación, entonces el menú muestra solo las opciones que permiten sus permisos en su administración (recurso de capacidades); las opciones aún no construidas aparecen deshabilitadas. _Prueba:_ backend, `CapabilitiesTest` y `MenuCatalogTest` (PR #55); faltan el menú del frontend (T22) y el e2e (T23)
- [ ] **001-16** — Dado un usuario sin un permiso, cuando intenta abrir esa opción por URL o llamar a su API, entonces recibe "sin permiso" (403) aunque la opción esté oculta en el menú. _Prueba:_ backend, `RequiresPermissionTest` y `AuthorizationRulesTest` (PR #55); faltan el frontend por URL (T21–T22) y el e2e (T23)
- [ ] **001-17** — Dados dos usuarios de administraciones distintas, entonces cada uno solo obtiene el contexto, las capacidades y los datos de la suya, y la administración activa nunca se toma de un parámetro enviado por el navegador. _Prueba:_ pendiente

**Auditoría, idioma y accesibilidad**

- [x] **001-18** — Dados inicios de sesión exitosos y fallidos, bloqueos, expiraciones, cierres de sesión y cambios de roles hechos en el proveedor de identidad, entonces quedan en la auditoría inmutable de LIV con usuario (o el nombre intentado, enmascarado), fecha y resultado, sin contraseñas ni códigos; los eventos originados en LIV llevan además su `correlation_id`. _Enmienda 1: los eventos del proveedor se copian a la auditoría de LIV._ _Prueba:_ `SessionAuditTest`, `IdentityProviderImportTest` y `KeycloakIdentityProviderEventsTest` (backend, contra Keycloak real; PR #60)
- [ ] **001-19** — Las páginas de inicio de sesión, segundo factor y error están en español, usan los estilos de LIV (tokens de `liv-components`) y cumplen WCAG 2.1 AA (verificado con axe‑core). _Prueba:_ pendiente. T08 cubre el español y el uso exclusivo de *tokens* en las páginas de acceso (`identity/theme/check.sh`, PR #57 y #58); faltan axe y la prueba visual (T23) y las pantallas del frontend (T22)

**Independencia del proveedor de identidad**

- [ ] **001-20** — Dado el BFF y el backend, entonces autentican solo con OpenID Connect / OAuth 2.0 estándar: funcionan igual con *tokens* de un emisor OIDC genérico de prueba que con los de Keycloak, y ningún código de LIV usa librerías ni API propias de Keycloak para autenticar. _Prueba:_ pendiente
- [ ] **001-21** — Dado un usuario que inicia sesión, entonces LIV lo identifica con su propio identificador, vinculado al par (emisor, `sub`) del proveedor; la auditoría y el contexto de sesión usan el identificador de LIV, de modo que cambiar de proveedor solo cambia ese vínculo. _Prueba:_ pendiente

## Fuera de alcance

- Recuperar u olvidar contraseña por correo electrónico (requiere servicio de correo; feature posterior).
- "Mi cuenta": cambiar la contraseña, activar de forma voluntaria el segundo factor y ver las sesiones abiertas (feature posterior, con su propio diseño; enmienda 1).
- Administración de usuarios, roles y permisos desde LIV (menú 1.2, feature posterior). En esta feature el equipo técnico da de alta los usuarios, su rol y su administración en la consola web de Keycloak; el menú 1.2 lo hará desde LIV a través de un puerto propio, sin que el resto de LIV dependa de Keycloak.
- Usuarios con más de una administración y selector de administración activa (feature posterior).
- Filtrar el menú por las **modalidades** activas de la jurisdicción: depende de la configuración de jurisdicción publicada (menú 1.5.9), que aún no existe. Esta feature filtra solo por permisos y deja el recurso de capacidades preparado para agregar ese filtro.
- Federación con un directorio de la entidad e inicio de sesión del contribuyente (portal, P9).
- Las pantallas de negocio a las que lleva el menú.

## Decisiones propuestas para aprobar con esta spec

_Valores que no fija ningún documento funcional. Al aprobar H1 quedan aceptados; se pueden cambiar después por configuración, sin tocar código._

| # | Tema | Valor propuesto | Origen |
|---|---|---|---|
| D-001-1 | Bloqueo por intentos fallidos | 5 intentos seguidos → 15 minutos de bloqueo | `docs/arquitectura/02` §3 no fija cifras |
| D-001-2 | Expiración de la sesión | 15 minutos de inactividad; 8 horas como máximo | `docs/arquitectura/02` §3 los marca "a confirmar" |
| D-001-3 | Administración por usuario | Una sola administración por usuario en esta feature | `docs/arquitectura/02` §4.1 |
| D-001-4 | Segundo factor | TOTP (aplicación de autenticación); obligatorio para los roles de 001-06 | `docs/arquitectura/02` §3 |
| D-001-5 | Permisos de las opciones del menú | Se derivan de las matrices de Doc. 01 §4 y Doc. 04 §10; para los menús 2.x y 3.x, que no tienen matriz, se propone una en el `plan.md` | Doc. 01 §4, Doc. 04 §10 |
| D-001-6 | Independencia del proveedor de identidad | El proveedor solo dice quién es el usuario, su rol y su administración; los permisos (`<modulo>.<recurso>.<accion>`) los resuelve LIV. Un único adaptador traduce el *token* al usuario de LIV, la gestión de cuentas (menú 1.2) pasa por un puerto propio y la configuración del *realm* se versiona en el repositorio. Así Keycloak se puede reemplazar por otro proveedor OIDC (p. ej. un servidor propio con Spring Authorization Server) cambiando configuración y adaptadores, no el dominio ni el frontend | ADR 0010; `docs/arquitectura/02` §3–§4 |

## Preguntas abiertas

_Ninguna que cambie un criterio de aceptación: los valores sin respaldo documental se proponen en la tabla anterior._
