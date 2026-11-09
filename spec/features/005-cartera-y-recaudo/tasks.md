# 005 · Cartera y recaudo: documentación funcional, configuración y esquema 1.1 — Tareas

_Checklist accionable derivada del `plan.md`. Tareas pequeñas y concretas; cada una cabe en un PR revisable (≤ ~400 líneas). Marca `[x]` al completarlas. Cada tarea indica:_

- _su proyecto y los archivos que puede tocar;_
- _de qué otras tareas depende;_
- _qué recursos compartidos usa: un contrato, la versión de `liv-components`, las migraciones o los archivos de *build* de su proyecto, el entorno local;_
- _la prueba que se escribe primero (TDD: rojo → verde → refactorizar)._

_Con eso el orquestador decide qué corre en paralelo (`liv-orquestador`)._

> **Declarado en el plan (H2), para la fase de tareas.** Las tareas salen de los ocho PR del `plan.md` (R0, R1, R2, R3a, R3b, R4, B0 y B1) y de su orden. La tarea de R3a incluye, entre sus archivos, la copia binaria de `Formatos_Intercambio_Recaudo.xlsx` del insumo (`.entrada-claude-ai/` del checkout principal) a `docs/funcional/anexos/Formatos_Intercambio_Recaudo.xlsx`. Antes de subirla se comprueban sus 13.779 bytes y su SHA‑256 `27023a14ef160f1552eb8355a0ffd9ee82f7ecdf1a6a0defdd863b89cf0ff90c`, y se inspecciona como dato (plan, paso 3; criterio 005‑10). La persona dio el permiso de descarga y el orquestador ya la verificó contra el original.

- [ ] **T1** · <Tarea concreta de implementación>
  - **Proyecto:** `<backend/ · frontend/ · ui/ · e2e/ · raíz>`
  - **Archivos:** `<ruta>`
  - **Depende de:** <ninguna / T…>
  - **Recursos:** <ninguno / contrato `<nombre>` / versión de `liv-components` / migraciones / archivos de *build* / entorno local>
  - **Prueba primero:** <código de regla / nombre de la prueba>
- [ ] **T2** · <Tarea concreta de implementación>
  - **Proyecto:** `<…>`
  - **Archivos:** `<ruta>`
  - **Depende de:** <…>
  - **Recursos:** <…>
  - **Prueba primero:** <…>
- [ ] **T3** · <Tarea de pruebas de integración o e2e / validación>
  - **Proyecto:** `e2e/`
  - **Archivos:** `<ruta>`
  - **Depende de:** <…>
  - **Recursos:** entorno local
  - **Prueba primero:** <nombre de la prueba>
- [ ] Actualizar documentación si aplica (funcional, arquitectura, ADR, glosario).
- [ ] Revisión adversarial independiente de lo implementado; hallazgos resueltos o justificados.
- [ ] Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra.
- [ ] Compuertas de CI en verde y PR aprobado por un humano (H3).
- [ ] Mover la feature a "Hecho" en `../../constitution/roadmap.md` y actualizar `ESTADO.md`.

## Mantenimiento (checklist recurrente)

_Opcional. Pasos a repetir cada vez que se toque esta feature en el futuro. Borra esta sección si no aplica._

- [ ] <Acción recurrente, p. ej. recalcular los ejemplos *golden* si cambia una regla de la configuración.>
