---
name: liv-revision-adversarial
description: Revisión adversarial independiente del proyecto LIV con dos revisores ciegos que buscan fallos en un diseño técnico o en código. Es la única revisión de agentes del proyecto (ADR 0040) - úsala en cada PR que toca una zona roja, una regla con código o un documento funcional, sobre el diseño técnico de una feature o de una rebanada suelta con zona roja, y cuando el usuario pida "revisión adversarial", "judgment-day", "juzgar" o "buscar fallos". Reemplaza a judgment-day y a las cuatro lentes (review-risk, review-resilience, review-readability, review-reliability) de gentle-ai.
---

# Revisión adversarial independiente

Objetivo: que alguien distinto de quien escribió el trabajo intente demostrar que está mal. Quien implementó **no** se revisa a sí mismo (`AGENTS.md` §4.1, principio 10).

**Cuándo** (ADR 0040, `AGENTS.md` §10.1). Es la única revisión de agentes del proyecto:
- en cada PR que toca una zona roja, una regla con código o un documento funcional (`AGENTS.md` §4.3);
- sobre el diseño técnico de `spec.md` de una feature con zona roja, antes de la H1;
- sobre el diseño técnico de una rebanada suelta con zona roja, antes de «Lista»;
- cuando la persona la pida, sobre cualquier diseño o código.

El resto de los PR entra con el CI en verde y la H3 de la persona.

## 1. Definir el objetivo

- **Diseño:** `spec.md` de la feature (reglas, criterios, diseño técnico y rebanadas), o el diseño técnico del *issue* de una rebanada suelta, y los documentos funcionales citados.
- **Implementación:** el diff del PR frente a `main`, más `spec.md` y el *issue* de la rebanada.

## 2. Lanzar dos revisores ciegos

- Si tu herramienta permite subagentes, lanza **dos en paralelo**, con contexto nuevo. Ninguno ve el resultado del otro.
- Si no los permite, haz **dos pasadas separadas**, idealmente en sesiones nuevas, y no relees la primera hasta terminar la segunda.
- Cada revisor recibe solo el objetivo y esta lista, nunca la opinión de quien implementó.
- **Nivel de modelo** (`AGENTS.md` §10):
  - **Alto** si el objetivo toca una zona roja, una regla con código o un documento funcional (§4.3). En Claude Code, dos subagentes `liv-revisor-critico`.
  - **Estándar** cuando la persona la pide fuera de esos casos. En Claude Code, dos subagentes `liv-revisor`.
  - En otras herramientas se usa el modelo equivalente.
- **Encargo cerrado:** rutas de los archivos y secciones de las fuentes que deben leer, no su contenido pegado.

Cada revisor busca, como mínimo:

| Tema | Pregunta |
|---|---|
| Fidelidad funcional | ¿Cada criterio de `spec.md` se cumple tal como dice el documento funcional y su código de regla? ¿Hay reglas del documento que faltan? |
| Dinero y cálculo | ¿Aritmética exacta (`BigDecimal`), redondeo solo al final, ningún valor tributario en el código? ¿Los ejemplos *golden* dan al peso? |
| Invariantes del dominio | ¿Se respeta que lo publicado no se modifique, que los saldos no se guarden, la segregación cargador ≠ aprobador y el aislamiento por `administration_id`? |
| Casos límite | Bordes de rangos ("hasta" / "más de"), vigencias, fechas de causación, vehículos nuevos, archivos vacíos o duplicados, reintentos |
| Arquitectura | ¿Respeta las capas, las fronteras entre módulos y la independencia backend/frontend (`AGENTS.md` §4.6)? ¿Un caso de uso por clase, sin «servicios» que crecen con cada rebanada? |
| Seguridad | ¿Autorización en el caso de uso (no solo en la interfaz), segregación de funciones, RLS y aislamiento entre administraciones, entradas validadas, SQL parametrizado, archivos cargados seguros (tipo real, tamaño, XLSX y YAML), inyección de fórmulas en exportaciones (`docs/arquitectura/02`)? ¿Ningún dato personal en registros, URL ni errores, y ningún secreto en el código? ¿Cada caso de uso expuesto va completo, con su permiso y su segregación? |
| Resiliencia | ¿Qué pasa si falla a mitad de camino? Idempotencia (`file_sha256`, `idempotency_key`), transacciones y publicación atómica, procesos masivos que se reanudan, concurrencia (bloqueo optimista, transición de estado condicionada), errores como Problem Details con código de regla y sin trazas internas |
| Datos | ¿Las migraciones nuevas usan *expand/contract* si cambian una tabla existente? ¿Ninguna migración aplicada se edita? ¿Ningún borrado ni actualización en tablas *append-only*? |
| Código limpio | Reglas CC‑01 a CC‑12 (`docs/arquitectura/04` §1): `Dinero` en lugar de `double`, reloj inyectado, controladores sin lógica, nombres del glosario, comentarios que explican el porqué y citan la regla. En el frontend, solo `liv-components` y estilos por *token* |
| Pruebas | ¿Cada regla y cálculo tributario tiene su prueba con su código de regla, escrita antes (`AGENTS.md` §4.1, principio 5)? ¿Las pruebas fallarían si el código estuviera mal? ¿Hay pruebas que no verifican nada? ¿Hay pruebas de integración con PostgreSQL real, RLS activa y la matriz de permisos? |
| Alcance y deuda | ¿Se tocó algo fuera de la rebanada? ¿Se resolvió un pendiente funcional con un supuesto? ¿El alcance del MVP deja fuera algo que una regla exige? ¿Hay atajos sin su *issue* `deuda` aprobado o `TODO` sin *issue*? |

Formato de cada hallazgo: **severidad** (crítico / alto / medio / bajo), **ubicación** (`archivo:línea` o sección), **regla o criterio afectado**, **por qué está mal** y **corrección propuesta**.

## 3. Consolidar

| Caso | Tratamiento |
|---|---|
| Lo encontraron los dos | **Confirmado**: se corrige |
| Lo encontró uno solo | **Sospechoso**: se verifica contra la fuente (documento, prueba, código). Si se confirma, se corrige; si no, se descarta explicando por qué |
| Contradice un documento funcional | Se detiene y se pregunta al humano; no se "corrige" la norma |

## 4. Corregir y volver a juzgar

1. Corrige los hallazgos confirmados (con TDD si es código).
2. Repite la revisión solo sobre lo corregido, con el mismo nivel de modelo: el encargo lista los puntos corregidos y las secciones cambiadas. **Máximo dos rondas**; si en la segunda siguen apareciendo críticos o altos, detente y escala al humano.

## 5. Registrar

- Para un **diseño**: en la línea **Revisión** del diseño técnico de `spec.md`, anota la fecha, el número de hallazgos por severidad, qué se corrigió y qué se descartó con su motivo.
- Para una **implementación**: el mismo resumen en la descripción del PR.
- No digas que la revisión pasó si quedaron hallazgos críticos o altos sin resolver.
