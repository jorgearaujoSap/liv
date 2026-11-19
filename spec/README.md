# spec/ — SDD por rebanadas verticales

> Una spec corta por feature y un backlog de rebanadas verticales que se construyen de punta a punta (ADR 0040). El esfuerzo va donde cuesta cambiar después: datos, contratos, reglas tributarias y seguridad. Las reglas completas están en `AGENTS.md` §4.

## Estructura

```
spec/
├── constitution/            ← reglas estables del proyecto (cambian poco)
│   ├── mission.md           ← qué construimos y para quién
│   ├── tech-stack.md        ← tecnologías, convenciones y límites
│   └── roadmap.md           ← orden y estado de las features
└── features/                ← una carpeta por feature
    ├── NNN-nombre-feature/  ← plantilla: se copia, no se edita
    └── 009-nombre-feature/
        └── spec.md          ← qué hace, reglas, alcance del MVP, criterios, diseño técnico y rebanadas
```

El backlog de cada feature son *issues* de GitHub con la etiqueta `feature:NNN`, en el *Project* `liv-project` (columnas *Backlog*, *Ready* = «Lista», *In progress* = «En curso», *In review* = «En revisión» y *Done* = «Hecha»; *skill* `liv-backlog` §0).

Las features 001 a 008 se planificaron con el método anterior y conservan sus `plan.md` y `tasks.md`, como historial.

## Tamaños de trabajo

Antes de empezar, decide el tamaño (`AGENTS.md` §4.3):

| Tamaño | Qué es | Qué lleva |
|---|---|---|
| **Ajuste** | Errata, formato, defecto con causa clara fuera de las reglas y de las zonas rojas, refactorización local, parche, *script*, prueba nueva fuera de las zonas rojas, alinear una pantalla con su diseño | Un PR directo, con su prueba si cambia el comportamiento; sin revisión de agentes |
| **Rebanada** | Una pieza de una feature que se ve funcionando de punta a punta, o una rebanada suelta sin feature (un defecto en una zona roja o en una regla, una deuda, algo de «Después») | Su *issue* y uno a tres PR, uno por proyecto |
| **Feature** | Una opción del menú o capacidad nueva, un cambio de regla, un módulo, más de dos rebanadas | `spec.md` (H1) y su backlog |

En una **zona roja** (`AGENTS.md` §4.3), y en todo código que implementa o corrige una regla con código, un cambio de código nunca es un ajuste y cada PR pasa la revisión adversarial. Una rebanada suelta con zona roja lleva además un diseño técnico corto en su *issue*, con su revisión adversarial antes de «Lista».

## Flujo de una feature

> Procedimiento detallado para personas y agentes: *skill* `liv-sdd-feature`. Para avanzar varias rebanadas a la vez, *skill* `liv-orquestador`.

1. **Arranque.** Copiar la plantilla `features/NNN-nombre-feature/` a una carpeta con el siguiente número libre y un nombre corto en minúsculas con guiones. Escribir `spec.md`: qué hace y por qué, reglas, alcance del MVP, criterios, diseño técnico en una página y rebanadas en orden.
   - Si toca una zona roja, su diseño técnico pasa la revisión adversarial (`liv-revision-adversarial`).
   - **H1:** la persona aprueba la spec.
2. **Backlog.** Un *issue* por rebanada, con la etiqueta `feature:NNN` y el formato de abajo. Lo que quedó en «Después» va al backlog sin feature.
3. **Diseño.** Figma una rebanada por delante: la pantalla tiene su diseño aprobado antes de empezar la rebanada que la construye (`AGENTS.md` §6.2).
4. **Rebanada.** Con el visto bueno de la persona en el chat, el agente la mueve a «Lista» (ADR 0041). Primero su parte del contrato; después el backend y el frontend, en paralelo si el contrato ya está. El PR del frontend se integra después de la *release* del contrato. Se ve funcionando en local y se ajusta.
5. **PR.** Uno por proyecto, con sus pruebas, el CI en verde y una captura si hay pantalla. Si toca una zona roja, una regla con código o un documento funcional, pasa la revisión adversarial (dos revisores ciegos). Enlaza su *issue* (`Parte de #n` o `Cierra #n` en el último). **H3:** la persona revisa e integra.
6. **Cierre.** Pruebas de navegador de los caminos principales, cada criterio de `spec.md` marcado `[x]` con su prueba, deuda revisada, la opción abierta en el menú, y roadmap, glosario y `ESTADO.md` al día.

La opción del menú queda en «Próximamente» hasta el cierre, y cada caso de uso que una rebanada expone va completo, con su permiso y su segregación, así que `main` siempre está sano. Los cambios de alcance se anotan en la sección «Cambios» de `spec.md`, sin enmiendas formales; los que tocan un criterio, el diseño técnico o una zona roja llevan el visto bueno de la persona, y un cambio de una regla tributaria sigue el proceso de los documentos funcionales. Los PR de los proyectos no tocan `spec.md`, el glosario ni `ESTADO.md` (`AGENTS.md` §4.2, «Quién escribe en la raíz»).

> La constitución manda: si una feature choca con `mission.md` o `tech-stack.md`, se replantea la feature, no la constitución. Cambiar la constitución exige un ADR aprobado.

## Formato del *issue* de una rebanada

Título: `NNN · R<n> · <qué se podrá hacer>` (una rebanada suelta: `<módulo> · <qué se podrá hacer>`). Etiquetas: `feature:NNN` si es de una feature, `rebanada` y, si toca una zona roja, `zona-roja`. El agente la crea en «Backlog» y mantiene el tablero; la mueve a «Lista» solo con el visto bueno de la persona en el chat.

```markdown
**Objetivo:** <qué podrá hacer o ver la persona usuaria al terminar>

**Criterios** (de `spec.md` si es de una feature):
- [ ] <código> — Dado …, cuando …, entonces …

**Proyectos:** backend/ · ui/ · frontend/ · e2e/
**Zona roja:** <no | cuál>
**Diseño:** <nodo de Figma aprobado | sin pantalla>
**Contrato:** <endpoints y esquemas, o «sin cambio»>
**Pruebas que la demuestran:** <unitarias del dominio, integración del endpoint, pantalla>
**Fuera de esta rebanada:** <lo que va en otra>
**Diseño técnico** (solo una rebanada suelta con zona roja): <datos con *expand/contract*, contrato, permisos, amenazas y riesgos; revisión adversarial: fecha y hallazgos>

**Definición de hecha** (`AGENTS.md` §4.2):
- [ ] criterios con su prueba en verde
- [ ] CI en verde en cada PR
- [ ] si hay zona roja o regla, revisión adversarial sin críticos ni altos abiertos
- [ ] captura o video si hay pantalla
- [ ] `CHANGELOG.md` de cada proyecto
- [ ] ningún `TODO` sin *issue*; atajos con *issue* `deuda` aprobado, o corregidos
- [ ] funciona de punta a punta en local
```

Un atajo o una deuda técnica se registra en un *issue* con la etiqueta `deuda`: qué se dejó, por qué y cómo se paga. Los *issues* nuevos que encuentra un agente se le proponen a la persona y se crean solo con su aprobación (*skill* `liv-backlog`).

## Equivalencia con las fases SDD de gentle‑ai

| Fase gentle‑ai | Dónde queda |
|---|---|
| explore | No produce archivo; sus hallazgos alimentan `spec.md` |
| propose + spec + design | `spec.md` (diseño técnico incluido) |
| tasks | *Issues* de las rebanadas |
| apply | Código y pruebas, en los PR de cada rebanada |
| verify | Criterios marcados en `spec.md` al cierre |
| archive | `constitution/roadmap.md` + `ESTADO.md` |
