# ADR-0028 · SDD adaptativo: evaluación previa antes de la especificación

- **Estado:** Propuesto
- **Fecha:** 2026-11-05
- **Decisores:** responsable del proyecto, que lo pidió el 2026-11-05 (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** ADR 0014; `AGENTS.md` §2, §4.1, §4.3, §10 y §11; `docs/arquitectura/04` §8 (Q11) y §9; `docs/arquitectura/05` §1, §3.3 y §12; `spec/README.md`; *skill* `liv-sdd-feature`

## Contexto

El ADR 0014 hace obligatorio el proceso SDD + TDD. Hoy solo quedan fuera del SDD los cambios **triviales** (texto, formato, parche de dependencia). Cualquier otro cambio, aunque sea pequeño, claro y sin riesgo, crea una carpeta de feature con `spec.md`, `plan.md` y `tasks.md`. Algunos ejemplos son corregir un defecto con causa conocida, agregar pruebas, ajustar la documentación o un *script* de desarrollo.

Con eso, el costo de documentar supera al del cambio, y el SDD pierde peso donde sí importa: reglas de negocio, contratos, datos, seguridad y arquitectura. El responsable pidió conservar el SDD como metodología principal, pero que el agente evalúe cada solicitud antes de escribir una especificación. La idea es tomar de ODD el comportamiento adaptativo, sin instalarlo ni traer su estructura documental.

## Opciones consideradas

1. **Mantener la clasificación actual.**
   - A favor: no cambia nada.
   - En contra: sigue el costo de documentar los cambios pequeños, o la tentación de llamar «trivial» a lo que no lo es.
2. **Instalar ODD o una metodología paralela.**
   - En contra: duplica reglas y artefactos, y lo excluye el pedido del responsable.
3. **Evaluación previa dentro del SDD.**
   - Antes de la especificación, el agente comprueba criterios concretos. Si se cumple alguno, aplica el SDD de siempre. Si no se cumple ninguno y la tarea es sencilla, clara y de impacto acotado, la implementa directamente, con las mismas pruebas, verificaciones y aprobación humana del PR.
   - A favor: una sola metodología y una sola tabla de clasificación, y el riesgo decide.
   - En contra: depende del juicio del agente. Se compensa con criterios verificables, con reevaluar durante la implementación, con declarar el flujo en el PR y con la revisión H3.

## Decisión

Se elige la opción 3. El ADR 0014 sigue vigente: el SDD + TDD es la metodología principal. Este ADR enmienda su alcance: cambia la consecuencia del principio G1 (doc. 05 §1), que eximía solo a los cambios triviales, y define cuándo un cambio no necesita especificación. Se aplica desde su aceptación.

- **Evaluación previa.** Es el paso 2 de `AGENTS.md` §2, y sus criterios están en §4.3, que reemplaza el tipo «trivial» por el **flujo directo**. Si se cumple al menos un criterio, el cambio va por el SDD (normal o crítico, como hasta hoy):
  - reglas de negocio o tributarias, incluido un defecto que hace aplicar mal una regla;
  - contratos o datos;
  - rutas críticas o seguridad, con una excepción acotada para comentarios, `README.md`, `CHANGELOG.md` y parches de dependencias;
  - arquitectura (módulos, dependencias);
  - interfaz;
  - ambigüedad que la persona no aclaró, o más de un PR o de ~200 líneas.
- **Fuera del flujo directo.** Los documentos funcionales, los ADR, la arquitectura, la constitución y las reglas de los `AGENTS.md` nunca van por el flujo directo: siguen su propio proceso de versión, ADR y aprobación.
- **Flujo directo.** Sin carpeta de feature ni documentos de planificación.
  - Prueba primero si cambia el comportamiento, verificaciones locales y CI en verde.
  - Si cambia el comportamiento, un revisor independiente (`liv-revisor`) revisa la corrección antes del PR. Si no, basta la lente de legibilidad. En los dos casos, después viene la H3. En el equipo de una persona, la H3 la hace quien abre el PR, así que esa revisión independiente es el control de la corrección.
  - La descripción del PR declara el flujo y por qué no aplica ningún criterio.
  - Se documenta en el `CHANGELOG.md` del proyecto que toca y en la bitácora de `ESTADO.md`. La raíz no tiene `CHANGELOG.md`, así que sus cambios quedan en `ESTADO.md`.
- **Reevaluación.** Si durante una implementación directa aparece un criterio, el agente se detiene, se lo informa a la persona y el cambio sigue por el SDD. Ante la duda, también va por el SDD (G8).
- **Sin cambios en el resto:** las prohibiciones de §4.5, las aprobaciones H1–H3, las compuertas de CI, la excepción del equipo de una persona y el flujo SDD completo.

## Consecuencias

- **Positivas:**
  - menos documentos sin valor en los cambios pequeños, y el SDD concentrado donde reduce riesgo;
  - una sola tabla de clasificación, sin metodología paralela;
  - cada PR dice qué flujo usó, así que la H3 puede devolverlo al SDD.
- **Negativas o costos:**
  - un agente puede clasificar mal. Se mitiga con criterios explícitos, con la reevaluación, con la regla «ante la duda, pregunta y, si sigue, SDD» y, cuando exista, con `sdd-guard`, que comprobará lo medible de un PR directo (doc. 04 §6, Q11);
  - una nueva métrica en el doc. 05 §12 vigila los PR directos devueltos al SDD.
- **Qué cambia en el repositorio:**
  - `AGENTS.md` §2, §4.1, §4.3, §10.1 y §11;
  - `docs/arquitectura/05` §1, §3.3 y §12;
  - `docs/arquitectura/04` (Q11 y nombres de rama);
  - `docs/arquitectura/00` §6;
  - `spec/README.md`;
  - la *skill* `liv-sdd-feature`;
  - los `AGENTS.md` de `backend/`, `backend/bff/`, `frontend/` y `ui/`, en un PR por proyecto (`AGENTS.md` §4.6).
