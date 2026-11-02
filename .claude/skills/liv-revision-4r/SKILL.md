---
name: liv-revision-4r
description: Revisión de un cambio del proyecto LIV con cuatro lentes (riesgo, resiliencia, legibilidad y confiabilidad). Úsala antes de cada commit con la lente de legibilidad, y antes de abrir un PR con las cuatro lentes cuando el cambio toca rutas críticas o supera 400 líneas. También cuando el usuario pida "revisión 4R", "revisar riesgo", "revisar legibilidad" o similares. Reemplaza a review-risk, review-resilience, review-readability y review-reliability de gentle-ai.
---

# Revisión con cuatro lentes (4R)

## Cuándo y qué lentes

| Momento | Lentes |
|---|---|
| Antes de cada commit | **Legibilidad** (rápida, sobre el diff preparado) |
| Antes de cada *push* | **Legibilidad** |
| Antes de abrir el PR, si toca rutas críticas (`AGENTS.md` §4.3) **o** supera 400 líneas cambiadas | Las **cuatro**, en paralelo si la herramienta permite subagentes |

**Nivel de modelo por lente** (`AGENTS.md` §10). Entre paréntesis, el subagente de Claude Code; en otras herramientas se usa el modelo equivalente.

| Lente | Cambio normal | Cambio crítico (§4.3) |
|---|---|---|
| Riesgo | Estándar (`liv-revisor`) | Alto (`liv-revisor-critico`) |
| Resiliencia | Estándar (`liv-revisor`) | Estándar (`liv-revisor`) |
| Legibilidad | Económico (`liv-revisor-rapido`) | Económico (`liv-revisor-rapido`) |
| Confiabilidad | Estándar (`liv-revisor`) | Estándar (`liv-revisor`) |

El objetivo es el diff frente a `main` (o el diff preparado para el commit). Cada lente produce hallazgos con: **severidad** (crítico / alto / medio / bajo), `archivo:línea`, **regla afectada** y **corrección propuesta**. Una lente sin hallazgos lo dice explícitamente.

## Lente 1 — Riesgo

¿Qué daño puede causar este cambio si sale mal?

- Seguridad: autorización en el caso de uso (no solo en la interfaz), segregación de funciones, RLS y aislamiento entre administraciones, entradas validadas, SQL parametrizado, archivos cargados (tipo real, tamaño, antivirus, XLSX y YAML seguros), inyección de fórmulas en exportaciones (`docs/arquitectura/02`).
- Datos personales: nada en logs, URL ni errores; datos de prueba sintéticos.
- Dinero: ¿puede crear, duplicar o perder un movimiento, un pago o un descuento?
- Acciones irreversibles: borrados, actualizaciones de tablas *append‑only*, migraciones destructivas sin *expand/contract*.
- Cadena de suministro: dependencias nuevas sin ADR, secretos en el código.

## Lente 2 — Resiliencia

¿Qué pasa cuando algo falla a mitad de camino?

- Idempotencia: reintentos y reinicios (`file_sha256`, `idempotency_key`, número de fila, vehículo ya emitido).
- Transacciones: publicación atómica; ¿qué queda si falla a la mitad?
- Procesos masivos: bloques con confirmación, reinicio desde el último bloque, control de presión.
- Concurrencia: bloqueo optimista, transición de estado condicionada, `SKIP LOCKED`.
- Integraciones: tiempos de espera, reintentos con *backoff*, eventos entregados más de una vez.
- Errores: Problem Details con código de regla, sin trazas internas.

## Lente 3 — Legibilidad

¿Lo entiende alguien nuevo sin preguntar?

- Reglas CC‑01 a CC‑12 (`docs/arquitectura/04` §1): sin valores tributarios en el código, `Dinero` en lugar de `double`, reloj inyectado, controladores sin lógica, inyección por constructor.
- Nombres del glosario (`docs/arquitectura/01` §15): código en inglés, interfaz y documentación en español.
- Comentarios que explican el **porqué** y citan la regla (`// LQ-C04, Doc. 02 §B.8`).
- Funciones cortas, un solo nivel de abstracción, sin duplicación.
- Frontend: solo componentes de `liv-components`, estilos solo por *token*, sin `style` ni `::ng-deep`.

## Lente 4 — Confiabilidad

¿Las pruebas demuestran que funciona y seguirá funcionando?

- TDD: commit `test:` antes del `feat:`/`fix:`; la prueba lleva el código de la regla.
- Cada criterio de `spec.md` tiene una prueba que fallaría si el código estuviera mal (sin pruebas tautológicas).
- Ejemplos *golden* de los documentos funcionales, propiedades (sumas, rangos continuos, redondeo) y casos límite.
- Pruebas de integración con PostgreSQL real (Testcontainers), RLS activa, matriz de autorización y aislamiento entre administraciones.
- Determinismo: sin dependencia del reloj, del orden o de datos compartidos entre pruebas.

## Resultado

1. Una tabla por lente con sus hallazgos.
2. Corrige los críticos y altos antes de seguir; los medios y bajos, o se corrigen o se justifican.
3. Para las cuatro lentes, pega el resumen (hallazgos por severidad y qué se hizo) en la descripción del PR.
