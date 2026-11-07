# 002 · Publicación automática de liv-components — Tareas

_Checklist accionable derivada del `plan.md`. Tareas pequeñas y concretas; cada una cabe en un PR revisable (≤ ~400 líneas). Cada tarea indica los archivos que puede tocar y la prueba que se escribe primero (TDD: rojo → verde → refactorizar). Marca `[x]` al completarlas._

- [ ] **T1** · <Tarea concreta de implementación> — Archivos: `<ruta>` — Prueba primero: <código de regla / nombre de la prueba>
- [ ] **T2** · <Tarea concreta de implementación> — Archivos: `<ruta>` — Prueba primero: <código de regla / nombre de la prueba>
- [ ] **T3** · <Tarea de pruebas de integración o e2e / validación> — Archivos: `<ruta>`
- [ ] Actualizar documentación si aplica (funcional, arquitectura, ADR, glosario).
- [ ] Revisión adversarial independiente de lo implementado; hallazgos resueltos o justificados.
- [ ] Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra.
- [ ] Compuertas de CI en verde y PR aprobado por un humano (H3).
- [ ] Mover la feature a "Hecho" en `../../constitution/roadmap.md` y actualizar `ESTADO.md`.

## Mantenimiento (checklist recurrente)

_Opcional. Pasos a repetir cada vez que se toque esta feature en el futuro. Borra esta sección si no aplica._

- [ ] <Acción recurrente, p. ej. recalcular los ejemplos *golden* si cambia una regla de la configuración.>
