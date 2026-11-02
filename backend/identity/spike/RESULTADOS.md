# Spike de Keycloak — resultados (feature 001, tarea T06)

- **Fecha:** 2026-11-01
- **Versión probada:** Keycloak **26.8.1** (`quay.io/keycloak/keycloak:26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332`), la misma del entorno local de `backend/dev/`.
- **Método:**
  - un *realm* de prueba `spike`, creado y borrado por la API de administración, con bloqueo por fuerza bruta a los 3 intentos, idioma `es` y eventos de usuario y de administración activados;
  - un cliente confidencial y dos usuarios ficticios (uno activo, uno desactivado);
  - un script que recorre el flujo de inicio de sesión del navegador (formulario incluido) y las API de *token*, revocación, sesiones y eventos;
  - para la pregunta 2, un tema mínimo que vuelca los datos que recibe la plantilla `login.ftl`.

  Nada de esto queda en el repositorio salvo este informe.
- **Para qué:** el `plan.md` de la feature 001 (paso 3, paso 10 y "Riesgos") depende de estas cuatro respuestas.

## Resumen

| # | Pregunta | Respuesta | Efecto en el plan |
|---|---|---|---|
| 1 | ¿Puede el tema distinguir "cuenta bloqueada" de "credenciales incorrectas"? | **No.** Keycloak muestra el mismo mensaje genérico a la cuenta bloqueada | **Decisión del responsable** (enmienda 1 de 001-04) |
| 2 | ¿Puede el tema recibir un motivo (`logout`, `expired`, `denied`) por un parámetro propio? | **No.** La plantilla no recibe los parámetros de la petición | **Decisión del responsable** (mensaje "Tu sesión terminó") |
| 3 | ¿Revocar el *refresh token* (RFC 7009) cierra la sesión del usuario en Keycloak? | **Sí.** Borra la sesión y el siguiente inicio pide credenciales | Se confirma el plan |
| 4 | ¿Qué eventos emite y cómo se leen? | Fallos con su causa, bloqueo, inicio, cierre, revocación y eventos de administración con representación. **No hay evento de expiración** | Se confirma el plan (el barrido del BFF registra las expiraciones) |

## 1. Cuenta bloqueada (criterio 001-04)

**Evidencia:**

| Intento | Mensaje de la página |
|---|---|
| Usuaria existente, contraseña mala, intentos 1 a 3 | "Usuario o contraseña incorrectos." |
| Misma usuaria ya bloqueada (intento 4, y luego con la contraseña **correcta**) | "Usuario o contraseña incorrectos." |
| Usuario inexistente, intentos 1 a 4 | "Usuario o contraseña incorrectos." |
| Usuario desactivado, contraseña correcta | "La cuenta está desactivada, contacte con el administrador." |

- **El bloqueo sí ocurre.** La API de detección de ataques muestra `disabled: true`, `numFailures: 3` y `numTemporaryLockouts: 1` para la usuaria, y quedan los eventos `USER_DISABLED_BY_TEMPORARY_LOCKOUT` y `LOGIN_ERROR (user_temporarily_disabled)`.
- **El mensaje no cambia.** Ante el bloqueo, Keycloak usa a propósito el mismo texto que ante credenciales incorrectas, para no revelar qué usuarios existen. La plantilla solo recibe ese texto, así que el tema no tiene cómo saber que la cuenta está bloqueada.

**Conclusión.** Mostrar el aviso de "cuenta bloqueada" que pide la enmienda 1 exige una **extensión de Keycloak en Java** (un *authenticator* propio en `backend/identity/`, que se mantiene con cada actualización de Keycloak). El plan lo prevé como riesgo y pide **llevarlo al responsable antes de escribirlo**.

**Hallazgo adicional (001-05).** Para la cuenta desactivada Keycloak muestra un mensaje distinto, que revela que el usuario existe. Se corrige sin código: el tema redefine ese mensaje en `messages_es` con el texto genérico, como ya prevé el plan (paso 3).

## 2. Motivo en el tema (`logout`, `expired`, `denied`)

**Evidencia:**
- Se pidió la autorización con el parámetro propio `liv_reason=expired-marca-unica`. Ese valor **no aparece en ninguna parte** de la página generada.
- La plantilla recibe estos datos: `msg`, `execution`, `social`, `auth`, `requiredActionUrl`, `title`, `locale`, `login`, `pageId`, `url`, `advancedMsg`, `authenticationSession`, `themeResources`, `messagesPerField`, `templateName`, `client`, `passwordPolicies`, `realm`, `darkMode`, `kcSanitize`, `scripts`, `lang` y `properties`.
- Ninguno de ellos trae los parámetros de la petición:
  - `url.loginAction` solo lleva `session_code`, `execution`, `client_id`, `tab_id` y `client_data`;
  - `authenticationSession` solo expone identificadores.

**Conclusión.** Para que la página de Keycloak muestre "Tu sesión terminó", hace falta otra **extensión en Java**. La alternativa sin código que prevé el plan es que el BFF lleve primero al usuario a una página pública de LIV que diga por qué terminó la sesión, con un botón para volver a iniciar sesión. Esa página no tiene diseño aprobado.

## 3. Revocación del *refresh token* (RFC 7009)

**Evidencia:**

| Paso | Resultado |
|---|---|
| Inicio de sesión por el navegador y canje del código | 1 sesión activa de la usuaria |
| `POST …/protocol/openid-connect/revoke` con el *refresh token* | HTTP 200 |
| Sesiones de la usuaria después de revocar | **0** |
| Renovar con el *refresh token* revocado | HTTP 400 `invalid_grant` (`Session not active`) |
| Nueva autorización con la *cookie* de sesión del navegador | **Pide credenciales** (no reabre la sesión) |

**Conclusión.** Se confirma el plan: el BFF cierra la sesión del proveedor desde el servidor (paso 10, `SessionTerminator`), sin enviar `id_token_hint` ni ningún *token* al navegador. No hace falta la variante con la API de administración de Keycloak.

**A tener en cuenta.** El *token* de acceso ya emitido sigue siendo un JWT válido hasta que vence (≤ 5 minutos), porque la API lo valida localmente. Por eso el plan revalida en cada petición contra el registro de LIV (`CurrentUserFilter`).

## 4. Eventos

**Eventos de usuario registrados** (`GET /admin/realms/{realm}/events`):

| Tipo | Causa (`error`) | Uso en 001-18 |
|---|---|---|
| `LOGIN_ERROR` | `invalid_user_credentials` | Intento fallido de un usuario existente |
| `LOGIN_ERROR` | `user_not_found` | Intento con un usuario inexistente; trae el nombre intentado en `details` y **se enmascara** al copiarlo |
| `LOGIN_ERROR` | `user_temporarily_disabled` | Intento durante el bloqueo |
| `LOGIN_ERROR` | `user_disabled` | Usuario desactivado |
| `USER_DISABLED_BY_TEMPORARY_LOCKOUT` | — | **El bloqueo** |
| `LOGIN`, `CODE_TO_TOKEN`, `LOGOUT`, `REVOKE_GRANT` | — | No se copian (los registra LIV), para no duplicar |

**Eventos de administración** (`GET /admin/realms/{realm}/admin-events`): cada cambio hecho en la consola o por la API queda con:
- `operationType` (`CREATE`, `UPDATE`, `DELETE`);
- `resourceType` (`USER`, `REALM_ROLE_MAPPING`, `REALM_ROLE`, `REALM`, `CLIENT`, `USER_LOGIN_FAILURE`);
- `resourcePath`, el autor (`authDetails.userId`) y la **representación** del recurso cuando está activado el detalle.

Con esto se sabe qué rol se asignó y qué atributo cambió (por ejemplo, `administration_id`). La lista blanca de campos del importador debe filtrar la representación.

**Expiración.** Entre los 132 tipos de evento de esta versión, los únicos relacionados con sesiones son `USER_SESSION_DELETED` y `USER_SESSION_DELETED_ERROR`. **No hay un evento de expiración** por inactividad ni por máximo. Se confirma el plan: el barrido del BFF registra las expiraciones (`SessionEnded(IDLE|ABSOLUTE)`).

**Cómo se leen.** Con la API de administración y un cliente propio de *client credentials* (`liv-audit-importer`), con los roles de solo lectura de eventos y usuarios del *realm*.

## Otros hallazgos

- **Cookies `Secure` también por HTTP.** Keycloak 26 marca `AUTH_SESSION_ID`, `KC_AUTH_SESSION_HASH` y `KC_RESTART` como `Secure` aunque se sirva por HTTP. Los navegadores las aceptan en `localhost` y `127.0.0.1`, así que el desarrollo local funciona. Un cliente HTTP de pruebas, en cambio, debe enviarlas igual (lo tendrán en cuenta las pruebas de integración del BFF).
- **Idioma.** Con `es` como idioma del *realm*, los mensajes estándar ya salen en español. El tema solo redefine los que deben cambiar.

## Decisiones pendientes para el responsable

Según la regla del *spike* y los riesgos del plan, el trabajo se detiene aquí hasta decidir:

1. **Aviso de cuenta bloqueada (001-04, enmienda 1).**
   - (a) Escribir el *authenticator* propio en Java en `backend/identity/`, con su mantenimiento en cada actualización de Keycloak;
   - (b) volver al mensaje único para todos los fallos, que agrega "tras varios intentos fallidos el acceso se bloquea por 15 minutos", sin código y sin revelar qué usuarios existen.
2. **Mensaje "Tu sesión terminó" (001-09, 001-10, 001-22).**
   - (a) Escribir una extensión en Java;
   - (b) una página pública de LIV antes del inicio de sesión, que necesita diseño y aprobación;
   - (c) sin mensaje: tras cerrar o expirar la sesión se muestra directamente la página de inicio de sesión. Los criterios de la spec no exigen el mensaje; solo el diseño lo mostraba.
