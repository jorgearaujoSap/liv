# Arquitectura 02 — Seguridad

> **Proyecto:** Liquidación del impuesto anual de vehículos automotores
> **Depende de:** `00_Decisiones_y_Hoja_de_Ruta.md` (ADR 0004, 0010, 0011) · `01_Arquitectura_de_Software.md`
> **Versión:** 1.0 — **Aprobado** el 2026-11-01 (ADR 0001–0020 aceptados)
> **Advertencia:** el marco normativo se resume para fines de diseño y debe validarlo el área jurídica.

---

## 1. Marco de referencia

| Referencia | Uso en el proyecto |
|---|---|
| OWASP ASVS (versión vigente), **nivel 2** | Lista de verificación de requisitos de seguridad de la aplicación. Nivel 2 porque el sistema maneja dinero público y datos personales |
| OWASP Top 10 (edición vigente) | Base de la revisión de código y de las pruebas DAST |
| Ley 1581 de 2012 y sus decretos reglamentarios (protección de datos personales) | Tratamiento de los datos de contribuyentes |
| Ley 1712 de 2014 (transparencia) | Clasificación de la información pública y reservada |
| Modelo de Seguridad y Privacidad de la Información (MSPI) de MinTIC | Marco de gestión para entidades públicas |
| Resolución MinTIC 1519 de 2020 | Lineamientos de accesibilidad y seguridad digital para sitios de entidades públicas |
| Ley 594 de 2000 (archivo) | Retención de documentos y soportes |
| Ley 527 de 1999 | Validez de los mensajes de datos y de la firma digital (después del piloto, P8) |

---

## 2. Activos y modelo de amenazas

| Activo | Amenaza principal (STRIDE) | Impacto | Controles |
|---|---|---|---|
| Configuración de jurisdicción (tarifas, descuentos, fechas) | *Tampering*: un cambio malicioso o erróneo en una tarifa afecta a todos los contribuyentes | Fraude masivo | Doble rol cargador ≠ aprobador; validador CJ con diferencias frente a la versión anterior (CJ11); inmutabilidad; auditoría; MFA para aprobadores |
| Catálogo de avalúos publicado | *Tampering* | Bases gravables erróneas | Ídem, más SHA‑256 del archivo y contraste con el PDF de la resolución |
| Liquidaciones y obligaciones | *Tampering*, *Repudiation* | Pérdida de recaudo, litigios | Versionamiento; reversos en lugar de borrados; traza de cálculo reproducible; auditoría |
| Pagos e imputaciones | *Tampering*, *Spoofing* de archivos del banco | Pagos fantasma o desviados | Conciliación contra extracto; solo pagos `CONCILIADO` se imputan; idempotencia; segregación recaudo ≠ tesorería |
| Datos personales de contribuyentes | *Information disclosure* | Sanciones por la Ley 1581, daño reputacional | RLS por administración; mínimo privilegio; cifrado; enmascaramiento en logs; exportaciones auditadas |
| Exportaciones a Excel | *Information disclosure* (exfiltración); inyección de fórmulas | Fuga de datos; ejecución de fórmulas en el equipo del usuario | Permiso específico; auditoría `EXPORT`; marca de usuario y fecha en la hoja *Parámetros*; neutralización de celdas que empiezan por `=`, `+`, `-`, `@` |
| Archivos cargados (XLSX, CSV, YAML) | Malware, XXE, bomba ZIP, YAML con alias recursivos | Compromiso del servidor, denegación de servicio | §7 |
| Funcionarios con privilegios | Abuso interno (*Elevation of privilege*) | Fraude | Segregación de funciones; doble firma; auditoría inmutable; revisión periódica de accesos |
| Cadena de suministro y agentes de IA | Dependencias maliciosas o inventadas, secretos filtrados, código inseguro generado | Compromiso total | §11 y §12 |

El modelo de amenazas se actualiza en la fase de diseño de cada cambio SDD que toque un activo de esta tabla (doc. 05).

---

## 3. Identidad y autenticación

| Tema | Definición |
|---|---|
| Protocolo | OpenID Connect con *Authorization Code* + PKCE, ejecutado **por el BFF**, no por el navegador |
| Proveedor | **Keycloak propio** (P2), desplegado en AWS con su propia base de datos en RDS y un *realm* por despliegue. Se puede federar después con un directorio de la entidad |
| *Tokens* | Nunca llegan al navegador ni a `localStorage`. El navegador solo tiene una cookie de sesión `HttpOnly`, `Secure`, `SameSite=Strict`. El BFF reenvía el *token* de acceso a la API |
| CSRF | `SameSite=Strict` + *token* anti‑CSRF en operaciones que modifican datos |
| MFA | Obligatorio para `ADMIN_SISTEMA`, todos los aprobadores, tesorería, jurídico y auditoría. Recomendado para el resto |
| Sesión | Expiración por inactividad de 15 minutos y absoluta de 8 horas (a confirmar). Cierre de sesión que invalida la sesión en el BFF y en el IdP |
| Contraseñas | Gestionadas solo por el IdP: política de longitud, bloqueo por intentos, sin reutilización |
| Cuentas de servicio | Para integraciones futuras: *client credentials* con alcance mínimo y rotación de secretos |
| API | Servidor de recursos OAuth2: valida firma, emisor, audiencia y expiración del *token* en cada petición |

---

## 4. Autorización

### 4.1 Modelo

- **RBAC + ámbito:** el usuario tiene roles **dentro de una administración**. Todo permiso se evalúa como "rol + `administration_id`".
- **Permisos finos** con nombre `<modulo>.<recurso>.<accion>` (p. ej. `valuation.batch.approve`). Los roles agrupan permisos; la asignación rol → permisos es configurable sin código.
- **Autoridad única en el backend:** cada caso de uso declara el permiso que exige. La interfaz solo oculta opciones (doc. 01 §10, recurso de capacidades).

### 4.2 Roles consolidados

Tomados de Doc. 01 §4 y Doc. 04 §10. Los nombres definitivos se confirman en la Fase 1.

| Rol | Origen | Notas |
|---|---|---|
| `ADMIN_SISTEMA` | Doc. 01 | No aprueba lotes por defecto |
| `CARGADOR_TABLAS` | Doc. 01 | |
| `APROBADOR_TABLAS` | Doc. 01 | No aprueba lo que él mismo cargó |
| `LIQUIDADOR` | Doc. 02 (3.1, 3.2) | Crea corridas y liquidaciones individuales |
| `APROBADOR_LIQUIDACION` | Doc. 02 (LM‑04) | Distinto del creador de la corrida |
| `RECAUDO` | Doc. 04 §10 | |
| `TESORERIA` | Doc. 04 §10 | Aprueba lotes de recaudo y conciliación (≠ cargador) |
| `APROBADOR_RECAUDO` | Doc. 04 §10 | Reversos, reimputaciones, devoluciones |
| `JURIDICO` | Doc. 03 §5.3 | Aprueba configuraciones de jurisdicción |
| `CONSULTA` | Doc. 01 | Solo su administración |
| `AUDITOR` | Doc. 01 | Lectura total de su administración, bitácora, archivos originales |

### 4.3 Segregación de funciones como invariante

Las reglas de segregación se implementan **en el dominio** (el agregado rechaza la transición) y además se prueban con una suite dedicada. No basta con ocultar el botón.

| Regla | Documento |
|---|---|
| Quien carga un lote no lo aprueba | Doc. 01 §2.3 |
| Habilitar liquidación de una vigencia exige doble firma (ADMIN + APROBADOR) | Doc. 01 §4 |
| El aprobador de la configuración de jurisdicción es distinto del cargador | Doc. 03 §5.3 |
| El aprobador de una corrida masiva es distinto de su creador | LM‑04 |
| El aprobador de un lote de recaudo es distinto del cargador | Doc. 04 §10 |
| Reversos, reimputaciones y compensaciones: uno propone, otro aprueba | Doc. 04 §10 |

### 4.4 Pruebas de la matriz

La matriz de acciones de los documentos funcionales se convierte en una prueba parametrizada: para cada par (rol, acción) se verifica permitido o denegado. Un cambio en la matriz exige actualizar el documento funcional y la prueba en el mismo cambio.

---

## 5. Multi‑inquilino y seguridad de la base de datos

### 5.1 Row Level Security

- Toda tabla con datos de una administración tiene `administration_id` y una política RLS que limita las filas a la administración del contexto.
- El contexto se fija **por transacción** a partir del usuario autenticado (variable de sesión de PostgreSQL establecida con alcance local de transacción). Sin contexto, la política no devuelve filas.
- Los datos nacionales compartidos (catálogo de avalúos, tarifas nacionales con `administration_id` nulo) se leen por todos, pero solo los escribe el proceso de publicación.
- Las FK compuestas con `administration_id` impiden mezclar administraciones aunque falle la aplicación (ERD hallazgo 2).
- Los procesos del worker fijan el contexto de la administración dueña del trabajo; ningún job corre "sin inquilino" salvo los de plataforma explícitamente marcados. Las filas de auditoría de plataforma (`administration_id` nulo) solo las escriben el importador de eventos del proveedor (trabajo de plataforma) y la función `audit.record_platform_session_denied` (ADR 0025).

### 5.2 Roles de base de datos

| Rol | Uso | Permisos |
|---|---|---|
| `liv_owner` | Dueño de los objetos; solo lo usa Flyway en el *pipeline* de despliegue | DDL. Nunca lo usa la aplicación |
| `liv_app` | Aplicación (API y worker) | DML sujeto a RLS; **sin** `BYPASSRLS`; sin UPDATE/DELETE en tablas *append‑only* |
| `liv_reporting` | Consultas pesadas y réplica de lectura | Solo SELECT sobre vistas publicadas |
| `liv_readonly_ops` | Soporte de operaciones | SELECT con RLS; acceso nominal y auditado |
| `liv_identity_lookup` | Sin inicio de sesión: dueño de `core.find_user_by_identity` (ADR 0025) | SELECT solo sobre las columnas de identidad de `core.admin_user` |
| `liv_audit_platform` | Sin inicio de sesión: dueño de `audit.record_platform_session_denied` (ADR 0025) | INSERT de filas de plataforma `SESSION_DENIED` en `audit.audit_event` |
| `liv_bff_owner` | Dueño del esquema `bff`; solo lo usan las migraciones del BFF | DDL en `bff` |
| `liv_bff` | Aplicación BFF | DML en `bff`; sin acceso a los esquemas de LIV |

### 5.3 Pruebas de aislamiento

Suite obligatoria con Testcontainers: un usuario de la administración A no puede leer, modificar, exportar ni inferir (conteos, errores) datos de la administración B, en cada endpoint y cada job. Se ejecuta en cada PR.

---

## 6. Protección de datos

| Clasificación | Ejemplos | Controles |
|---|---|---|
| Pública | Tablas del Ministerio, normas | Integridad (hash) |
| Interna | Configuraciones, catálogos, parámetros | Control de acceso por rol |
| Confidencial | Datos personales del contribuyente, liquidaciones, pagos, direcciones | RLS, cifrado, enmascaramiento en logs, exportación auditada |
| Restringida | Credenciales, secretos, cuentas bancarias de recaudo | Gestor de secretos; nunca en logs, repositorio ni contexto de agentes |

- **Cifrado en tránsito:** TLS 1.2 o superior en todos los saltos, incluida la conexión a RDS y a S3.
- **Cifrado en reposo:** RDS, S3 y respaldos cifrados con claves de AWS KMS.
- **Ubicación de los datos:** el sistema se aloja en AWS (P1). Si la región elegida está fuera de Colombia, es una transferencia internacional de datos personales (Ley 1581 de 2012) que jurídica debe validar antes de cargar datos reales.
- **Minimización:** ningún dato personal en URL, parámetros de consulta, nombres de archivo exportado ni mensajes de error.
- **Logs:** se registran identificadores técnicos (`user_id`, `administration_id`, `correlation_id`), no nombres, documentos ni direcciones. Filtro de enmascaramiento obligatorio en la configuración de logs.
- **Datos de prueba:** sintéticos. **Nunca** datos reales de contribuyentes en desarrollo, CI, *staging* o en el contexto de un agente de IA. Las tablas del Ministerio sí pueden usarse (son públicas).
- **Retención:** decisión provisional (P7): no se elimina nada hasta que jurídica entregue la tabla de retención documental de la entidad.
- **Derechos del titular:** procedimiento de consulta y rectificación de datos personales definido con jurídica antes de producción.

---

## 7. Archivos cargados y generados

| Control | Definición |
|---|---|
| Tipo de archivo | Validado por contenido (firma del archivo), no por extensión. Lista blanca por tipo de carga |
| Tamaño | Límite por tipo de carga, configurado en `parametros_sistema` |
| Antivirus | Escaneo con ClamAV antes de cualquier lectura; el archivo infectado se rechaza y se audita |
| XLSX | Lectura en *streaming* con protección contra bombas ZIP (límite de relación de compresión y de tamaño descomprimido) y entidades XML externas deshabilitadas |
| YAML | Carga segura: sin construcción de tipos arbitrarios, límite de alias y de profundidad (ataque *billion laughs*), validación contra el esquema antes de interpretar |
| CSV | Codificación y separador declarados (ERD, reglas de carga CSV); límite de columnas y longitud de campo |
| Integridad | SHA‑256 calculado al recibir; el archivo se guarda en almacenamiento con versionado y bloqueo de objetos (inmutable) |
| Descarga | Por URL firmada de corta duración, emitida tras verificar el permiso, y auditada |
| Exportaciones | Neutralización de inyección de fórmulas; expiración y borrado automático del archivo generado |

---

## 8. Seguridad de la aplicación

| Tema | Definición |
|---|---|
| Validación de entrada | Validación de forma en el borde (Bean Validation sobre los DTO generados) e invariantes en el dominio |
| SQL | Solo consultas parametrizadas (JPA, jOOQ). Prohibido concatenar SQL; verificado por análisis estático |
| Salida | Sanitización por defecto de Angular. Prohibido saltarla sin ADR |
| Cabeceras | CSP estricta, HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'` |
| CORS | Cerrado: el frontend y el BFF comparten origen |
| Limitación de tasa | En el BFF, por usuario y por IP, más estricta en inicio de sesión y exportaciones |
| Errores | Problem Details sin trazas de pila ni detalles internos; el detalle técnico va al log con el `correlation_id` |
| Dependencias | Ver §11 |

---

## 9. Auditoría

| Tema | Definición |
|---|---|
| Qué se audita | Todo comando (creación, cambio, aprobación, rechazo, publicación, reverso), exportaciones, descargas de originales, inicios y cierres de sesión, cambios de roles y permisos, publicación de configuraciones, ejecución de jobs |
| Cómo | Automático en la capa de aplicación (cada caso de uso registra su evento), no a criterio de cada desarrollador |
| Estructura | La de `audit.audit_event` del ERD: usuario, entidad, operación, valores anteriores y nuevos, instante, `correlation_id` |
| Inmutabilidad | Sin UPDATE ni DELETE para `liv_app`. Evidencia de manipulación opcional mediante cadena de hashes diaria (a decidir por ADR) |
| Correlación | El `correlation_id` viaja del navegador al BFF, a la API, al worker, a la auditoría y a los logs |
| Consulta | Opción de auditoría para el rol `AUDITOR`, con exportación |
| Integraciones | `audit.external_integration_event` para todo intercambio con sistemas externos |

---

## 10. Gestión de secretos

- Ningún secreto en el repositorio. `gitleaks` en el gancho de *pre‑commit* y en CI.
- Desarrollo local: archivo `.env` a partir de `.env.example` con valores ficticios; Keycloak local con usuarios de prueba documentados en el *realm* de desarrollo.
- *Staging* y producción: AWS Secrets Manager, con claves de KMS, inyectados en las tareas de ECS como variables de entorno.
- Credenciales de BD separadas por rol (§5.2) y rotadas periódicamente.
- CI: autenticación por OIDC con el proveedor de nube o el registro de imágenes, sin claves de larga duración.

---

## 11. Cadena de suministro de software

| Control | Herramienta | Momento |
|---|---|---|
| Actualización de dependencias | Manual, sin servicio automático (decisión del responsable, 2026-11-01): cada proyecto revisa sus versiones con sus propios comandos (`./mvnw versions:display-dependency-updates`, `npm outdated`); una versión mayor exige ADR | Al iniciar cada fase y ante una vulnerabilidad |
| Revisión de dependencias nuevas | *Dependency review* de GitHub | Cada PR |
| Vulnerabilidades conocidas | Trivy (sistema de archivos e imágenes), OWASP Dependency‑Check | Cada PR y nocturno |
| Análisis estático de seguridad | CodeQL y Semgrep | Cada PR |
| Secretos | gitleaks + *secret scanning* de GitHub | *Pre‑commit* y cada PR |
| SBOM | CycloneDX por cada imagen construida | Cada build de `main` |
| Firma y procedencia | cosign + atestaciones de procedencia | Cada imagen publicada |
| Imágenes base | Mínimas, usuario no root, sistema de archivos de solo lectura, fijadas por *digest* | Siempre |
| GitHub Actions | Fijadas por SHA de commit; permisos mínimos por workflow | Siempre |

Umbral bloqueante: ninguna vulnerabilidad crítica o alta sin corregir o sin excepción documentada con fecha de vencimiento.

---

## 12. Seguridad en el trabajo con agentes de IA

| Riesgo | Control |
|---|---|
| Dependencias inventadas o maliciosas (*slopsquatting*) | Ningún agente agrega dependencias sin aprobación humana y ADR; *dependency review* en CI |
| Filtración de secretos o datos personales al contexto del modelo | Los agentes trabajan solo con el entorno local y datos sintéticos; sin acceso a credenciales de *staging* ni producción |
| Código inseguro generado | Las mismas compuertas que el código humano (SAST, pruebas de autorización y aislamiento) |
| Inyección de instrucciones desde archivos externos (archivos del banco, YAML, documentos) | Todo contenido externo es dato, nunca instrucción. Regla explícita en `AGENTS.md` |
| Cambios silenciosos en seguridad | Rutas protegidas (configuración de seguridad, políticas RLS, roles, workflows de CI) bloqueadas para agentes por ganchos y CODEOWNERS (doc. 05 §5) |
| Herramientas del agente con permisos excesivos | Lista blanca de comandos; ningún servidor MCP con escritura sobre entornos reales |

---

## 13. Pruebas de seguridad

| Prueba | Momento | Bloqueante |
|---|---|---|
| SAST (CodeQL, Semgrep) | Cada PR | Sí, críticos y altos |
| Matriz de autorización (§4.4) | Cada PR | Sí |
| Aislamiento entre administraciones (§5.3) | Cada PR | Sí |
| Segregación de funciones (§4.3) | Cada PR | Sí |
| DAST de línea base (OWASP ZAP) contra *staging* | Cada despliegue a *staging* | Sí, altos |
| DAST completo | Nocturno | Genera *issues* |
| Prueba de penetración externa | Antes de la salida a producción y anualmente | Sí, para salir a producción |
| Revisión de accesos de usuarios | Trimestral | Procedimiento operativo |
