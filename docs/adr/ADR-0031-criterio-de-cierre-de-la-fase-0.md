# ADR-0031 · Criterio de cierre de la Fase 0: entorno local y CI en verde, sin *staging*

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Decisores:** responsable del proyecto, que lo decidió en la sesión del 2026-11-07 al preparar la entrega del proyecto a un desarrollador (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** `docs/arquitectura/00` §8 y §8.1 (criterio de salida y entregables F0‑01 a F0‑12); ADR 0030 (pruebas de navegador en local); `spec/constitution/roadmap.md`; `ESTADO.md` §3

## Contexto

El criterio de salida de la Fase 0 (doc. 00 §8) es un *walking skeleton* desplegado en *staging* con el CI completo en verde. Mientras no se cumpla, ninguna feature de negocio puede empezar (`AGENTS.md` §1, `spec/constitution/roadmap.md`).

El ADR 0030 dejó para antes de producción lo que hace falta para desplegar: las imágenes publicadas, `infra/` con TLS y el CI de las pruebas de navegador (T24 de la feature 001). Por eso el criterio de *staging* no se puede cumplir sin adelantar todo ese trabajo.

Por su parte, el *walking skeleton* ya existe: la feature 001 (inicio de sesión, menú por permisos, sesión en el BFF y auditoría) funciona de extremo a extremo en el entorno local (`tools/dev/iniciar.sh`), con las pruebas de navegador de `e2e/` en verde. La documentación, las *skills*, los subagentes y la feature de referencia están listos para entregar el proyecto a un desarrollador. Quedan entregables de la Fase 0 a medio hacer o sin empezar (`ESTADO.md` §3).

## Opciones consideradas

1. **Mantener el criterio de *staging*.**
   - **A favor:** se valida el despliegue antes de la primera línea de negocio.
   - **En contra:** obliga a construir las imágenes, la infraestructura en AWS y el despliegue antes de cualquier feature, y retrasa la Fase 1 varias semanas, aunque el ADR 0030 ya los había dejado para antes de producción.
2. **Cerrar con el entorno local y el CI en verde, y llevar *staging* a la primera entrega a usuarios.**
   - **A favor:** la Fase 1 empieza sobre un esqueleto ya probado de extremo a extremo.
   - **En contra:** el despliegue se valida más tarde. Cada pendiente de la Fase 0 que no bloquea el cierre necesita su momento anotado, para que no quede olvidado.

## Decisión

Opción 2. **La Fase 0 se cierra cuando se cumplan las tres condiciones:**

1. El *walking skeleton* (feature 001) funciona en el entorno local, con las pruebas de navegador de `e2e/` en verde (ADR 0030).
2. El CI de cada proyecto está en verde y es **obligatorio** en la protección de `main`. Para eso, los *workflows* pasan a «correr siempre y omitir los trabajos que no aplican» (F0‑07).
3. Están hechos F0‑02 (un archivo por ADR para los ADR 0001–0022), F0‑10 (esquema JSON de la configuración de jurisdicción) y F0‑11 (pruebas *golden* en rojo).

**Los demás pendientes no bloquean el cierre.** Cada uno queda para el momento en que se necesita:

| Pendiente | Entregable | Cuándo |
|---|---|---|
| Plantilla de PR y ganchos del agente y de Git | F0‑03 | No bloquea el cierre; recomendado antes de que el desarrollador empiece |
| Cobertura, mutación, Sonar, Error Prone, NullAway, Stylelint y guardián SDD | F0‑07 | No bloquea el cierre; recomendado antes de que el desarrollador empiece |
| Publicación de imágenes | F0‑07 | Con la T24 de la feature 001, antes de *staging* |
| *Script* que regenera los *tokens* desde Figma | F0‑05a | Con el primer cambio de *tokens* |
| Regresión visual de `liv-components` | F0‑05a | Con la primera modificación de un componente existente |
| MinIO y ClamAV | F0‑06 | Con la primera feature que suba archivos (1.3 o 1.5.x) |
| Observabilidad | F0‑06 | Antes de *staging* |
| Tablas tributarias y lista de verificación del ERD previa a Flyway | F0‑08 | Con la feature que cree cada tabla |
| Decisiones humanas de la base de datos de la bitácora del 2026-11-02 | F0‑08 | Antes de la feature que las necesite |
| Log estructurado en JSON con enmascarado y datos personales | F0‑09 | Antes de *staging* |
| *Staging* | — | Antes de la primera entrega a usuarios, con la T24 de la feature 001. El criterio de salida de la Fase 1 ya lo exige (doc. 00 §8) |

## Consecuencias

- **Positivas:**
  - la Fase 1 puede empezar en cuanto se cumplan las tres condiciones, sin esperar la infraestructura;
  - cada pendiente tiene su momento anotado.
- **Negativas o costos:**
  - el despliegue se valida al final de la Fase 1, no al principio;
  - las barreras automáticas de F0‑03 y F0‑07 no son condición de cierre. Si el desarrollador empieza sin ellas, la revisión humana (H3) pesa más.
- **Qué cambia en el repositorio:**
  - el doc. 00 §8 cambia el criterio de salida de la Fase 0 y suma este ADR a la tabla de §6;
  - `ESTADO.md` §3 refleja el criterio de cierre y el estado de cada entregable;
  - `AGENTS.md` y `spec/constitution/roadmap.md` no cambian: dicen que ninguna feature de negocio empieza antes de cerrar la Fase 0, sin nombrar *staging*.
