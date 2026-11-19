---
name: liv-sdd-feature
description: Arranca y conduce una feature del proyecto LIV con SDD por rebanadas verticales (ADR 0040) - spec.md corto con su diseño técnico, backlog de rebanadas en GitHub Issues, construcción de cada rebanada de punta a punta y cierre. Úsala cuando el usuario pida una funcionalidad nueva, "crear una feature", "escribir la spec", "armar el backlog", "hacer la siguiente rebanada" o cerrar una feature en spec/features/. No la uses para los ajustes (AGENTS.md §4.3).
---

# Feature con SDD por rebanadas verticales

Reemplaza a los comandos SDD de gentle‑ai. Las reglas están en `AGENTS.md` §4 y el resumen en `spec/README.md`; esta *skill* es el procedimiento. El criterio de todo: **con cuidado en lo que es caro de cambiar** (datos, contratos, reglas tributarias, seguridad, límites entre módulos) **y rápido en lo que es barato** (pantallas, textos, código interno).

## 0. Antes de empezar

1. Lee `ESTADO.md` y `spec/constitution/roadmap.md`. Hay una sola feature en construcción a la vez; la siguiente puede preparar su arranque mientras tanto.
2. Decide el tamaño (`AGENTS.md` §4.3). Si es un **ajuste**, no uses esta *skill*: va directo a un PR. Si es una **rebanada** de una feature que ya arrancó, ve a §3.
3. Lee los documentos funcionales que toca la feature y anota las secciones y los códigos de regla (`R0xx`, `LQ-*`, `CJxx`, `CA-x.xx`…). Para buscar, usa el subagente `liv-explorador`.
4. **Modelo** (`AGENTS.md` §10): nivel alto para el arranque y los ADR; estándar para construir rebanadas.

## 1. Arranque: `spec.md`

1. Busca el siguiente número libre en `spec/features/` (ignora la plantilla `NNN-nombre-feature`) y copia la plantilla a `spec/features/<NNN>-<nombre-corto>/`.
2. Escribe `spec.md`. Es **corto**: lo justo para arrancar.
   - **Qué hace y por qué**, en cinco líneas.
   - **Reglas** con su código y su fuente. Si un pendiente funcional (`PENDIENTE`, `POR_CONFIRMAR`) puede cambiar una regla, **detente y pregunta**.
   - **Alcance del MVP:** «Entra» con lo mínimo útil y «Después» con todo lo demás. Es la sección que más tiempo ahorra: sé estricto.
   - **Criterios** Dado / Cuando / Entonces, cada uno con su código y su rebanada.
   - **Diseño técnico, en una página:** módulos, datos (con *expand/contract* si cambia una tabla existente), contrato, permisos, amenazas, pantallas, riesgos y decisiones. Lo caro de cambiar se decide aquí. Una decisión difícil de revertir lleva su ADR (`liv-adr`).
   - **Rebanadas en orden.** R1 es el esqueleto: el camino más delgado de punta a punta (opción del menú, pantalla mínima, endpoint y tabla, conectados). Las demás, por riesgo primero y valor después. Cada una cabe en uno a tres PR de unas 400 líneas.
3. **Si la feature toca una zona roja**, ejecuta `liv-revision-adversarial` sobre el diseño técnico y anota el resultado en su línea «Revisión».
4. Pide la **H1** a la persona. Sin H1 no hay backlog. Una spec con una pregunta abierta que pueda cambiar un criterio no se aprueba.
5. Crea el backlog (§2) y abre el PR de arranque con `spec.md` (con la tabla de rebanadas y sus *issues*), y en `ESTADO.md` la feature en construcción y su entrada de la bitácora (`liv-estado`).

## 2. Backlog

Sigue la *skill* `liv-backlog` §2:
- un *issue* por rebanada de la spec aprobada, en «Backlog»;
- su número, en la tabla «Rebanadas» de `spec.md`;
- lo de «Después», propuesto a la persona como rebanadas sueltas.

El agente mantiene el tablero (`liv-backlog`, ADR 0041). Mueve una rebanada a «Lista» solo con el visto bueno de la persona en el chat.

## 3. Construir una rebanada

1. **Lista:** pide el visto bueno de la persona en el chat y, cuando lo da, mueve la rebanada a «Lista» y anótalo en el *issue*. Antes, completa en el *issue* lo que falte de la definición de lista (`AGENTS.md` §4.2): diseño aprobado si hay pantalla (`liv-figma`), su parte del contrato y las pruebas. Si toca una zona roja, prepara ese detalle con el nivel alto; si es una rebanada suelta con zona roja, agrega su diseño técnico corto y pásale la revisión adversarial (`liv-revision-adversarial`). Si falta algo, pregúntalo; no lo inventes.
2. **Rama** por proyecto: `feat/<NNN>-r<n>-<proyecto>` (p. ej. `feat/009-r2-backend`).
3. **Contrato primero**, si la rebanada toca la API (`liv-endpoint`).
4. **Backend:** en las reglas y los cálculos tributarios, la prueba primero (que falle, código, refactorización), con el código de la regla en su nombre y su etiqueta; no hace falta un *commit* aparte. El resto (permisos, segregación, adaptadores) entra con sus pruebas en el mismo PR. Un caso de uso por clase, sin «servicios» compartidos que crezcan con cada rebanada, y cada caso de uso expuesto con su permiso y su segregación completos. Migraciones con `liv-migracion`, con *expand/contract* si cambian una tabla existente. Pruebas de integración del endpoint con la base real.
5. **Frontend:** la pantalla solo con `liv-components`, contra el contrato (`liv-pantalla`), con sus pruebas de lógica. Puede construirse en local contra la rama del contrato, pero su PR no se integra ni fija `frontend/api-spec/` hasta que exista la *release* del contrato. Si falta un componente, primero va a `ui/` (`liv-componente`), en su propio PR, y la persona publica la versión. La opción queda en «Próximamente».
6. **Verlo funcionando:** levanta LIV en local (`README.md` › «Ejecutar LIV en local») y revisa la pantalla contra su diseño. Ajusta en la misma sesión.
7. **Revisión adversarial** (`liv-revision-adversarial`) del PR que toca una zona roja, una regla con código o un documento funcional. Corrige los críticos y altos. Los demás PR van con el CI en verde y la H3 de la persona.
8. **PR** por proyecto, con:
   - `Parte de #n`, o `Cierra #n` en el último;
   - el resumen de la revisión adversarial, si la hubo;
   - una captura si hay pantalla;
   - el `CHANGELOG.md` del proyecto.

   No toques `ESTADO.md`, `spec.md` ni el glosario (`AGENTS.md` §4.2, «Quién escribe en la raíz»). La H3 y la integración son de la persona.
9. **Deuda y hallazgos:** propón a la persona cada atajo como *issue* `deuda`, y lo que encuentres fuera de la rebanada como *issue* nuevo (`liv-backlog` §3). Créalos solo con su aprobación; si no aprueba la deuda de un atajo, corrígelo en la misma rebanada. Si hay más de cinco *issues* `deuda` abiertos, avísale: la siguiente rebanada es de limpieza.
10. **Cambios de alcance:** anótalos con la fecha en el *issue* de la rebanada, y ajusta o agrega la rebanada. Si agregan, quitan o alteran un criterio, el diseño técnico o una rebanada con zona roja, pide antes el visto bueno de la persona y llévalos a la sección «Cambios» de `spec.md` en un PR pequeño de la raíz. Si cambia una regla tributaria, detente: sigue el proceso de los documentos funcionales. Los términos nuevos del glosario también se anotan en el *issue*.

## 4. Cerrar la feature

Cuando todas las rebanadas están hechas:

1. **Pruebas de navegador** de los caminos principales en `e2e/` (`liv-prueba-e2e`), en verde contra el entorno local (ADR 0030). Una captura de referencia nueva o cambiada la aprueba la persona.
2. **Criterios:** marca cada uno de `spec.md` con `[x]` y la prueba que lo demuestra. Sin prueba no se marca.
3. **Deuda:** revisa los *issues* `deuda` de la feature con la persona y decide cuáles se pagan antes de la siguiente.
4. **Menú:** abre la opción, que deja de mostrar «Próximamente».
5. **PR de cierre:**
   - la feature, en «Hecho» del roadmap;
   - los cambios y los términos nuevos anotados en los *issues*, en la sección «Cambios» de `spec.md` y en el glosario (`docs/arquitectura/01` §15);
   - los ADR, al día;
   - `ESTADO.md`, con `liv-estado`;
   - el estado «hecha» en `spec.md`.
