# Arquitectura 03 — Escalabilidad, entornos, Docker y operación

> **Proyecto:** Liquidación del impuesto anual de vehículos automotores
> **Depende de:** `00_Decisiones_y_Hoja_de_Ruta.md` (ADR 0002, 0007, 0008) · `01_Arquitectura_de_Software.md`
> **Versión:** 1.0 — **Aprobado** el 2026-11-01 (ADR 0001–0020 aceptados)

---

## 1. Requisitos no funcionales propuestos

Ningún documento funcional fija cifras de carga ni de disponibilidad. El **escenario de dimensionamiento está decidido** (P5, doc. 00 §7): producto multi‑inquilino para departamentos. La **disponibilidad, el RPO y el RTO los define la entidad** (P6); mientras llega el requisito formal se usan los valores de esta tabla. Las cifras de rendimiento son objetivos de diseño que se confirman antes de la Fase 3.

| Requisito | Objetivo propuesto | Referencia |
|---|---|---|
| Escenario de dimensionamiento | **Decidido (P5):** un departamento, hasta 1.000.000 de vehículos y 500 usuarios internos; Ipiales es el primer inquilino y es mucho menor | D16 |
| Consultas interactivas | p95 < 500 ms con filtros sobre índices | Consultas 1.6, 3.6, 4.7 |
| Liquidación individual y simulador | p95 < 1 s | 3.1, 3.3 |
| Validación de las tablas del Ministerio (16.090 referencias, 389.530 avalúos) | < 5 min | Doc. 03 §2 |
| Pre‑liquidación masiva | 1.000.000 de vehículos en < 2 h con 4 workers | LM‑05 |
| Exportación asíncrona | 500.000 filas en < 10 min | Doc. 02 §B.14 |
| Imputación diaria | Archivo diario de un banco en < 15 min | 4.3.1 |
| Disponibilidad | Supuesto hasta que la entidad lo defina (P6): 99,5 % en horario laboral; ventanas de mantenimiento fuera de él | — |
| Pérdida máxima de datos (RPO) | Supuesto (P6): 15 min | — |
| Tiempo máximo de recuperación (RTO) | Supuesto (P6): 4 h | — |
| Pico estacional | Enero a abril (pronto pago, Doc. 03 §9.6): capacidad ×3 sobre el promedio | Calendario |

Cada objetivo tiene una prueba de rendimiento con k6 (doc. 04 §5) que se ejecuta antes de cada versión.

---

## 2. Estrategia de escalabilidad

### 2.1 Por rol de ejecución

| Componente | Cómo escala | Límite que vigilar |
|---|---|---|
| Frontend y BFF | Horizontal, sin estado (sesión en PostgreSQL con Spring Session) | Conexiones al IdP |
| API | Horizontal, sin estado | Pool de conexiones a la BD |
| Worker | Horizontal: cada instancia toma particiones de un job (`SKIP LOCKED`, particionamiento de Spring Batch) | Escritura en BD y bloqueos |
| PostgreSQL | Vertical primero; réplica de lectura para `reporting` y exportaciones; PgBouncer cuando el número de réplicas lo exija | IOPS, autovacuum, tamaño de tablas de movimientos y auditoría |
| Amazon S3 | Sin límite práctico de capacidad | Costo de almacenamiento y de las versiones conservadas por Object Lock |

### 2.2 Caché

- Los datos **publicados** son inmutables (catálogos, configuraciones, tarifas): se guardan en caché local (Caffeine) con clave por versión. No hay problema de invalidación: una nueva versión es una nueva clave.
- No se usa caché para datos transaccionales (obligaciones, pagos, saldos).
- No se incorpora Redis mientras la caché local y la sesión en BD sean suficientes. Su incorporación requiere ADR.

### 2.3 Mensajería

Los eventos entre módulos usan el *outbox* de PostgreSQL (ADR 0008). Se reevalúa un *broker* (RabbitMQ o Kafka) solo si aparece alguno de estos disparadores:

- integración en tiempo real con sistemas externos que consuman eventos;
- más de un despliegue independiente que necesite los mismos eventos;
- latencia de entrega del *outbox* fuera del objetivo de forma sostenida.

### 2.4 Base de datos

| Práctica | Definición |
|---|---|
| Índices | Diseñados a partir de los filtros de cada consulta documentada (Doc. 01 §7, Doc. 02 §B.14, Doc. 04 §9) y revisados en la fase de diseño SDD |
| Particionamiento | `ledger.tax_account_movement` y `audit.audit_event` por año cuando superen el umbral definido en operación |
| Vista de saldos | `ledger.v_obligation_balance` materializada, refrescada por evento o por lote, siempre recalculable desde los movimientos |
| Límites | `statement_timeout` para consultas interactivas; los procesos largos van al worker |
| Monitoreo | `pg_stat_statements`, consultas lentas y crecimiento de tablas en el tablero de BD |

### 2.5 Procesos masivos

- Orientados a bloques (*chunks*) con confirmación por bloque y reinicio desde el último confirmado.
- Los insumos inmutables (catálogo publicado, reglas de jurisdicción) se cargan una vez por ejecución, no por fila.
- Escrituras masivas con jOOQ en lotes, no entidad por entidad.
- Control de presión: número máximo de jobs concurrentes por administración, para que una corrida no degrade la atención de usuarios.

---

## 3. Entornos

| Entorno | Propósito | Datos | Despliegue |
|---|---|---|---|
| Local | Desarrollo humano y de agentes de IA | Sintéticos + tablas públicas del Ministerio | Docker Compose |
| CI efímero | Pruebas de integración y e2e por PR | Sintéticos, creados por la prueba | Testcontainers / Compose dentro del *runner* |
| *Staging* | Validación funcional, DAST, rendimiento | Sintéticos o anonimizados irreversiblemente | AWS, igual que producción, en una cuenta de AWS separada |
| Producción | Operación | Reales | AWS, en su propia cuenta; promoción de la **misma imagen** (mismo *digest*) validada en *staging* |

- Configuración por variables de entorno (*twelve‑factor*); ningún valor de entorno dentro de la imagen.
- Las reglas tributarias **no** son configuración de entorno: viven en la configuración de jurisdicción publicada.
- *Feature flags* para funcionalidad incompleta integrada en `main` (necesario con *trunk‑based development*), con fecha de retiro obligatoria.

---

## 4. Docker

### 4.1 Imágenes

| Imagen | Construcción | Reglas |
|---|---|---|
| Aplicación (API y worker) | Multi‑etapa: compilación con JDK, ejecución con JRE mínimo; JAR por capas para aprovechar caché; CDS/AOT para arranque | Usuario no root, sistema de archivos de solo lectura, *healthcheck*, sin herramientas de compilación en la imagen final |
| BFF | Igual que la aplicación | Igual |
| Frontend | Compilación con Node; servido por nginx sin privilegios | Cabeceras de seguridad (doc. 02 §8); sin Node en la imagen final |
| Migraciones | Flyway con los scripts de la versión | Se ejecuta como paso del despliegue, **antes** de la aplicación; la aplicación no migra al arrancar en *staging* ni en producción |

- Imágenes base fijadas por *digest*; prohibida la etiqueta `latest`.
- Escaneo con Trivy, SBOM y firma (doc. 02 §11).

### 4.2 Docker Compose local

| Servicio | Perfil | Notas |
|---|---|---|
| PostgreSQL | `core` | Con los roles de doc. 02 §5.2 y RLS activa, igual que producción |
| Keycloak | `core` | *Realm* de desarrollo importado, con un usuario de prueba por rol y por administración |
| MinIO | `core` | Sustituye a Amazon S3 en local, con versionado y bloqueo de objetos activos |
| ClamAV | `core` | |
| Aplicación API + worker | `app` | Opcional: el desarrollador puede ejecutarla desde el IDE |
| BFF + frontend | `app` | |
| OpenTelemetry Collector, Prometheus, Grafana, Loki, Tempo | `obs` | Opcional |
| Datos de ejemplo | `seed` | Tablas 2026 del Ministerio, configuración de Ipiales y contribuyentes sintéticos |

Por la independencia entre proyectos (doc. 01 §14.1), este entorno se reparte así:

- `backend/dev/` tiene el compose de **sus** dependencias (PostgreSQL, Keycloak, MinIO, ClamAV) y datos de prueba;
- el frontend se desarrolla contra un servidor simulado generado desde su copia del OpenAPI, o contra una imagen publicada del backend;
- `infra/` (raíz) tiene el compose del **sistema completo**, que usa las imágenes publicadas por etiqueta, nunca `build: ../backend`. Es el que usa Playwright en CI y el que cualquiera levanta localmente para probar todo junto.

Cada `Dockerfile` vive en su proyecto y usa su carpeta como contexto de construcción.

---

## 5. Despliegue

**Decidido (P1, ADR 0020): nube pública AWS.** Se usan servicios gestionados equivalentes a los de otros proveedores, para no quedar atados a AWS: contenedores OCI, PostgreSQL estándar, almacenamiento con API S3 y OpenTelemetry.

| Pieza | Servicio de AWS | Notas |
|---|---|---|
| Frontend, BFF, API, worker, Keycloak | **ECS sobre Fargate** (contenedores sin administrar servidores) | Un servicio por contenedor; API y worker escalan por separado (CPU, memoria, cola de trabajos). EKS solo si un ADR lo justifica (p. ej. muchos departamentos con despliegues propios) |
| Entrada | Application Load Balancer + AWS WAF | TLS con certificados de ACM; limitación de tasa también en el BFF |
| Base de datos | **Amazon RDS for PostgreSQL** | Una instancia para la aplicación y una base aparte para Keycloak. Multi‑AZ y réplica de lectura según lo que defina la entidad en P6 |
| Archivos | **Amazon S3** | Versionado, Object Lock (inmutabilidad) y cifrado con KMS; URL firmadas de corta duración |
| Imágenes | Amazon ECR | Escaneo de imágenes además de Trivy; solo imágenes firmadas |
| Secretos y claves | AWS Secrets Manager + AWS KMS | Credenciales de BD por rol (doc. 02 §5.2) con rotación |
| Antivirus | ClamAV como servicio interno en ECS | Igual que en local |
| Observabilidad | OpenTelemetry Collector en ECS, exportando al stack de §6 o a sus equivalentes gestionados (Amazon Managed Prometheus/Grafana, CloudWatch) | Se decide en el entregable F0‑09 |
| Infraestructura como código | Terraform u OpenTofu en `infra/` | Herramienta exacta en un ADR de la Fase 0 |
| Cuentas | Cuentas de AWS separadas para *staging* y producción | Acceso de CI por OIDC, sin claves de larga duración |

**Región:** pendiente. Alojar los datos fuera de Colombia es una transferencia internacional de datos personales (Ley 1581 de 2012), así que jurídica debe validar el país de la región elegida antes de cargar datos reales.

| Práctica | Definición |
|---|---|
| Despliegue sin interrupción | Migraciones *expand/contract* compatibles con la versión anterior; despliegue gradual de los servicios de ECS |
| Base de datos | RDS con recuperación a un punto en el tiempo; Multi‑AZ si P6 lo exige |
| Promoción | *Staging* → producción con aprobación manual (entorno protegido de GitHub) |
| Reversión | Volver a la definición de tarea anterior (imagen anterior); las migraciones *expand/contract* lo permiten sin revertir el esquema |

---

## 6. Observabilidad

| Señal | Contenido | Herramienta |
|---|---|---|
| Logs | JSON estructurado con `correlation_id`, `administration_id`, `user_id`, módulo y caso de uso; sin datos personales | Loki |
| Métricas técnicas | Latencia, tasa y errores por endpoint; JVM; pool de conexiones; duración de jobs; tamaño del *outbox* | Micrometer → Prometheus |
| Métricas de negocio | Lotes por estado; corridas y excepciones por código; pagos sin imputar; partidas abiertas de conciliación por antigüedad (Doc. 04 §4.4); giros tardíos; reporte SIBGA vencido | Micrometer → Prometheus |
| Trazas | Navegador → BFF → API → BD; jobs del worker con un *span* por bloque | OpenTelemetry → Tempo |
| Tableros | Uno por módulo + uno de plataforma + uno de base de datos | Grafana |
| Alertas | Cada alerta enlaza a su *runbook* en `docs/operacion/runbooks/` | Grafana Alerting |

Objetivos de nivel de servicio (SLO) derivados de §1, con presupuesto de errores revisado mensualmente.

---

## 7. Respaldos, recuperación y retención

| Tema | Definición |
|---|---|
| BD | Respaldos automáticos de RDS con recuperación a un punto en el tiempo (RPO de minutos); instantáneas copiadas a otra región o cuenta para recuperación ante desastres |
| Archivos | S3 con versionado y Object Lock; replicación a otra región si P6 lo exige |
| Pruebas de restauración | Trimestrales, documentadas; un respaldo no probado no cuenta |
| Retención | **Decisión provisional (P7):** no se borra nada hasta que jurídica entregue la tabla de retención documental (Ley 594 de 2000) |

---

## 8. Mantenibilidad operativa

| Práctica | Definición |
|---|---|
| Versiones | Versionado semántico del producto; *changelog* generado desde Conventional Commits |
| Actualización de plataforma | Java: se parte de Java 21 LTS; el paso a una LTS posterior se decide con un ADR, evaluando el soporte de Spring Boot y de las herramientas. Spring Boot: mantenerse en una línea con soporte. Angular: cada versión mayor dentro de los 6 meses. PostgreSQL: evaluación anual de la versión mayor |
| Dependencias | Renovate con agrupación por ecosistema y actualizaciones de seguridad inmediatas |
| *Runbooks* | Uno por alerta y por procedimiento (restauración, reproceso de un lote, reverso masivo, rotación de secretos) |
| Deuda técnica | Registrada como *issue* con etiqueta, revisada en cada planificación; Sonar como medición de tendencia |
