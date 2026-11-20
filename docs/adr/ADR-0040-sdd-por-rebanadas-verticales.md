# ADR-0040 · SDD por rebanadas verticales: spec corta por feature, backlog de rebanadas y esfuerzo según lo que cuesta cambiar

- **Estado:** Aceptado (2026-11-18, por el responsable del proyecto); complementado por el ADR 0041 (el agente mantiene el tablero y mueve a «Lista» con el visto bueno de la persona en el chat); modificado en parte por el ADR 0043, propuesto el 2026-11-20 (la aprobación de la publicación de `liv-components` la decide la persona y la ejecuta el agente con su confirmación)
- **Fecha:** 2026-11-18
- **Decisores:** responsable del proyecto, en la sesión del 2026-11-18, con las observaciones del desarrollador que trabajó del 2026-11-08 al 2026-11-18 (`AGENTS.md` §4.3, «quien dirige el trabajo decide»)
- **Documentos relacionados:**
  - `AGENTS.md` §1 a §4.4, §6.2, §7, §9, §10 y §11;
  - `docs/arquitectura/01` §3 y §11.2; `docs/arquitectura/04` §4, §6 (Q11 y Q15), §8 y §9; `docs/arquitectura/05`;
  - `spec/README.md`, `spec/constitution/tech-stack.md` y `roadmap.md`;
  - la plantilla de feature; las *skills* de proceso y los subagentes revisores;
  - ADR 0014, 0019, 0027, 0028 y 0033.

## Contexto

LIV es un **MVP**. La metodología de la Fase 0 sirvió para fundar el proyecto: arquitectura, CI obligatorio, librería de componentes y una primera feature de punta a punta. Pero en la Fase 1 se volvió una cascada que consume más tiempo del que aporta:

- **Todo se planifica por adelantado.** La feature 008 lleva tres días de planificación (spec, diseño, plan y 78 tareas en 37 PR y seis oleadas) y todavía no tiene código.
- **Los ajustes llegan tarde.** En la 007, la primera pantalla que se pudo ver salió el tercer día. Un ajuste de alcance necesitó una enmienda completa, con H1, H2, tareas y siete PR. Solo los documentos de esa enmienda tomaron hora y media.
- **Todo lleva el mismo rigor.** Dos revisores ciegos y cuatro lentes en cada PR crítico, y la H1, la H2 y la H3 por separado, aunque las da la misma persona.
- **El estado cuesta PR propios.** Unos 10 de los 74 PR entre el 2026-11-08 y el 2026-11-18 solo movían el tablero de `ESTADO.md`, porque el orquestador era su único escritor (ADR 0033).
- **Los PR son grandes.** Muchos de la 007 pasaron de 800 líneas, con un tope recomendado de unas 400.

Lo que sí funcionó y no se quiere perder:
- el CI obligatorio y sus compuertas;
- la arquitectura hexagonal con un caso de uso por clase;
- la trazabilidad norma → código de regla → prueba;
- las invariantes del dominio;
- los proyectos separables;
- la librería única de componentes.

El objetivo es avanzar y ver resultados rápido, con la calidad mínima para que el producto siga siendo escalable y mantenible por cualquier equipo.

## Opciones consideradas

1. **Seguir con el SDD completo.** Es seguro, pero lento, y el costo crece con cada feature.
2. ***Vibe coding* sin estructura.** Es rápido al principio, pero en un sistema tributario lleva a reglas inventadas, deuda escondida y una arquitectura que se erosiona.
3. **SDD por rebanadas verticales.** El SDD queda para la planificación corta de cada feature. Después se construyen rebanadas verticales desde un backlog, con un esfuerzo que depende de lo que cuesta cambiar cada cosa. La calidad la vigilan sobre todo las barreras automáticas.

## Decisión

Opción 3. Su criterio es: **con cuidado en lo que es caro de cambiar después, rápido en lo que es barato.**

| Caro de cambiar: diseño y revisión adversarial | Barato de cambiar: rápido, y se ajusta |
|---|---|
| Modelo de datos y migraciones; contratos de la API; reglas tributarias y modelo del dominio; seguridad, permisos y RLS; límites entre módulos | Pantallas dentro de su diseño aprobado; código interno de un caso de uso; textos y mensajes; estilos dentro de los *tokens* |

«Barato» quiere decir menos documentos y sin revisión de agentes (el CI y la H3 bastan), no saltarse Figma ni las pruebas.

**1. Tres tamaños de trabajo**, que reemplazan la evaluación previa de seis criterios del ADR 0028:

- **Ajuste:**
  - erratas, formato y comentarios;
  - un defecto con causa clara que no toca una regla con código ni una zona roja;
  - una refactorización local, un parche de dependencia, *scripts* o pruebas nuevas fuera de las zonas rojas;
  - un cambio de pantalla que la alinea con su diseño aprobado.

  Va directo a un PR, sin *issue* ni spec, con el CI en verde y la H3 de la persona.
- **Rebanada:**
  - una pieza de una feature que se ve funcionando de punta a punta;
  - o una **rebanada suelta**, sin feature: un defecto en una zona roja o en una regla, una deuda, algo que quedó en «Después».

  Es un *issue* del backlog.
- **Feature:**
  - una opción del menú o una capacidad nueva, un cambio de una regla de negocio o un módulo;
  - o un trabajo de más de dos rebanadas.

  Lleva un `spec.md` corto y su backlog.

**Nunca es un ajuste:**
- el código que implementa o corrige una regla con código (`R0xx`, `LQ-*`, `CJxx`, `OB-xx`, `MO-xx` y los demás prefijos de `AGENTS.md` §5.3), esté donde esté. Es una rebanada y lleva la revisión adversarial;
- un cambio de código en una zona roja.

Si una solicitud admite más de una interpretación, el agente pregunta antes de construir.

**2. Zonas rojas.** Son las rutas críticas de antes, más tres agregados:
- autorización, persistencia con la administración en curso (`platform/**/authorization/**`, `platform/**/persistence/**`) y auditoría (`audit/**`);
- de `ui/**`, solo la modificación de un componente existente, sus capturas de referencia, los *tokens* y la configuración de *build* y de publicación.

La lista completa está en `AGENTS.md` §4.3. En una zona roja:
- cada PR pasa la **revisión adversarial**: dos revisores ciegos de nivel alto;
- una feature pasa la revisión adversarial de su diseño técnico antes de la H1;
- una rebanada suelta lleva en su *issue* un diseño técnico corto, preparado con el nivel alto y con su revisión adversarial antes de «Lista».

**3. Un solo `spec.md` por feature.** Se aprueba una sola vez, en la **H1, que ahora se llama «de arranque»** y absorbe la H2. Desaparecen `plan.md` y `tasks.md` en las features nuevas. Su contenido:
- qué hace y por qué;
- reglas con su código y su fuente;
- alcance del MVP («Entra» y «Después»);
- criterios Dado/Cuando/Entonces, incluidos los no funcionales;
- diseño técnico en una página: módulos, datos con *expand/contract*, contrato, permisos, amenazas, pantallas, riesgos y decisiones;
- rebanadas en orden;
- cambios;
- preguntas abiertas.

Ninguna spec se aprueba con una pregunta abierta que pueda cambiar un criterio.

**4. Rebanadas verticales desde un backlog en GitHub.**
- Cada rebanada es un *issue* con su objetivo, sus criterios con código de regla, los proyectos que toca, su diseño, su parte del contrato y las pruebas que la demuestran.
- El tablero es un *Project* de GitHub, con las columnas Backlog, Lista, En curso, En revisión y Hecha.
- La primera rebanada es el **esqueleto** de la feature: el camino más delgado de punta a punta. Las demás van por riesgo y por valor.
- El diseño en Figma va una rebanada por delante.
- Cada rebanada empieza por su parte del contrato. El frontend puede construir contra la rama del contrato **solo en local**: su PR no se integra ni fija `frontend/api-spec/` hasta que exista la *release* del contrato (ADR 0027).
- Una rebanada lleva de uno a tres PR, uno por proyecto (`AGENTS.md` §4.6). Está hecha cuando funciona de punta a punta en local.
- **Solo la persona mueve una rebanada a «Lista»:** es su visto bueno. Los agentes crean los *issues* en «Backlog» y nunca los mueven a «Lista».

**5. `main` siempre sano.**
- Cada PR entra con sus pruebas y el CI en verde.
- La opción del menú queda en «Próximamente» hasta el cierre.
- Una rebanada que expone un caso de uso lo expone **completo**: con su permiso, su segregación de funciones y sus reglas. Ningún control se deja para una rebanada posterior, y ninguna rebanada persiste artefactos oficiales (liquidaciones, movimientos, publicaciones) sin sus reglas completas.
- Al cerrar, se agregan las pruebas de navegador de los caminos principales, se revisa la deuda y se abre la opción en el menú.

**6. Calidad proporcional:**
- **Prueba primero solo en las reglas y los cálculos tributarios:** el código que implementa una regla con código y los casos *golden*. La prueba lleva el código de la regla y se ve fallar antes de implementar, sin un *commit* `test:` aparte; la revisión adversarial comprueba que cada regla tenga la suya.
- **Todo lo demás** (permisos, segregación, adaptadores, frontend y *scripts*) entra con sus pruebas en la misma rebanada, nunca después.
- **La única revisión de agentes es la adversarial:** dos revisores ciegos de nivel alto (`liv-revision-adversarial`). Se hace:
  - en cada PR que toca una zona roja, una regla con código o un documento funcional;
  - sobre el diseño técnico de una feature o de una rebanada suelta con zona roja;
  - cuando la persona la pide.

  El resto entra con el CI en verde y la H3 de la persona. Se retiran la *skill* de las cuatro lentes (`liv-revision-4r`) y el subagente `liv-revisor-rapido`; lo esencial de sus lentes (seguridad, resiliencia, datos y código limpio) pasa a la lista de la revisión adversarial.
- **PR de unas 400 líneas como máximo.**

**7. Cambios durante la construcción.**
- Se anotan en la sección «Cambios» de `spec.md`, sin enmiendas formales.
- Lleva el visto bueno de la persona, anotado con su fecha, todo cambio que agrega, quita o altera un criterio, el diseño técnico o una rebanada con zona roja.
- Si el cambio agrega algo de una zona roja, pasa la revisión adversarial antes de «Lista».
- Un cambio de una regla tributaria sigue el proceso de los documentos funcionales.

**8. Deuda visible y con tope.**
- **El agente propone y la persona decide** (*skill* `liv-backlog`): todo atajo y todo hallazgo fuera de la rebanada (defectos, mejoras, deuda) se le proponen a la persona como *issues*, y se crean solo con su aprobación. Si no aprueba la deuda de un atajo, el atajo se corrige en la misma rebanada.
- Ningún `TODO` se integra sin su *issue*.
- Con más de cinco *issues* `deuda` abiertos en el repositorio, la siguiente rebanada es de limpieza.
- La deuda se revisa al cerrar cada feature.
- Como máximo hay tres rebanadas en curso, y una por proyecto.

**9. `ESTADO.md` sin escritor único.**
- El avance de las rebanadas lo lleva el *Project* de GitHub, y los PR de los proyectos no tocan `ESTADO.md`, `spec.md` ni el glosario.
- Esos archivos de la raíz cambian solo en:
  - el PR de arranque, con la spec y la tabla de rebanadas con sus *issues*;
  - un PR pequeño cuando la persona aprueba un cambio de criterio o de diseño técnico;
  - el PR de cierre, con los criterios marcados, el glosario y el roadmap.
- Un ajuste se registra en el `CHANGELOG.md` de su proyecto; los de la raíz, en una línea de la bitácora.

**10. El orquestador pasa a ser coordinador del backlog**, con la misma *skill* `liv-orquestador`.
- Es opcional: prepara las rebanadas, las reparte en hasta tres sesiones (diseño, backend y frontend), revisa los resultados y cierra la feature.
- Hay una sola feature en construcción a la vez, pero la siguiente puede preparar su arranque mientras tanto.
- El detalle de una rebanada con zona roja se prepara con el nivel alto.

**Lo que no cambia:**
- las fuentes de verdad (`AGENTS.md` §3);
- las invariantes del dominio (§5);
- lo prohibido sin aprobación humana (§4.5);
- los proyectos separables (§4.6);
- las pantallas solo con `liv-components` y con diseño aprobado (§6, ADR 0019);
- el CI obligatorio;
- «quien dirige el trabajo decide» (§4.3);
- ningún agente integra, aprueba, publica ni acepta un ADR.

**Transición:**
- **La 008 es el piloto.** Se conservan su spec, su diseño aprobado y las decisiones H2‑1 a H2‑24 de su `plan.md`, que queda como referencia del diseño técnico. Sus 78 tareas se agrupan en rebanadas del backlog, en un cambio aparte.
- **Las features cerradas** conservan sus archivos.
- **Las referencias** a `plan.md`, `tasks.md` y la H2 en los `AGENTS.md` y las *skills* de `backend/`, `frontend/`, `ui/` y `e2e/` se actualizan en un PR por proyecto.
- **Las plantillas de *issue* y de PR** (`.github/`) van en un PR aparte, con la aprobación de la persona. El *Project* se crea con sus columnas.
- **Hasta que se configuren sus automatizaciones,** el *Project* lo mueve quien abre o integra el PR.
- **A las dos semanas del piloto** se miden el tiempo de una rebanada, del *issue* a `main`, y el retrabajo, y se ajusta este método.

## Consecuencias

- **Positivas:**
  - la planificación de una feature baja de días a horas;
  - la primera pantalla de una feature se ve el primer día, y los errores salen mientras la rebanada está fresca;
  - los PR son más pequeños y fáciles de revisar;
  - el esfuerzo de revisión va adonde está el riesgo;
  - desaparecen los PR de estado y las enmiendas formales.
- **Negativas o costos:**
  - hay menos detalle escrito antes de construir. Una decisión mal tomada en una rebanada se descubre más tarde que en un plan revisado. La mitigan el diseño técnico de la spec, la revisión adversarial en las zonas rojas y el visto bueno de la persona en «Lista»;
  - el seguimiento pasa a GitHub, fuera de los archivos del repositorio. Si el repositorio cambia de alojamiento, el backlog se exporta;
  - mientras no existan versiones previas automáticas de `liv-components` y del contrato:
    - una rebanada que necesita un componente nuevo espera a que la persona publique en npm;
    - el PR del frontend espera la *release* del contrato;
  - la cobertura y las pruebas de navegador al cierre dependen de la disciplina de cada rebanada, que vigilan el CI y la revisión.
- **Qué cambia en el repositorio:**
  - `AGENTS.md` §1 a §4.4, §6.2, §7, §9, §10 y §11, y `CLAUDE.md`;
  - `docs/arquitectura/01` §3 y §11.2, `docs/arquitectura/04` §4.1, §4.2, §6, §7, §8 y §9, y `docs/arquitectura/05`, con una versión nueva cada uno;
  - `spec/README.md`, `spec/constitution/tech-stack.md` y la nota del *roadmap*;
  - la plantilla de feature, con un solo `spec.md`;
  - las *skills* `liv-sdd-feature`, `liv-orquestador`, `liv-estado`, `liv-revision-adversarial` y `liv-figma`; la *skill* nueva `liv-backlog`; los subagentes `liv-revisor` y `liv-revisor-critico`; y se retiran la *skill* `liv-revision-4r` y el subagente `liv-revisor-rapido`;
  - la guía del desarrollador y `ESTADO.md`;
  - el doc. 00 §6 (las filas de los ADR 0014, 0019, 0028, 0033 y 0040) y el estado de los ADR 0014, 0019, 0028 y 0033, que se vuelve efectivo cuando este ADR se acepta.

  Este ADR modifica en parte los ADR 0014 y 0019, y reemplaza en parte los ADR 0028 y 0033:
  - **0014:** las compuertas pasan a ser la H1 de arranque, el visto bueno de cada rebanada y la H3. La prueba primero queda obligatoria solo en las reglas y los cálculos tributarios;
  - **0019:** la declaración de reutilización de estructura va en `spec.md` o en el *issue* de la rebanada;
  - **0028:** los tres tamaños de trabajo reemplazan a la evaluación previa;
  - **0033:** el orquestador coordina rebanadas y no es el único escritor de `ESTADO.md`. Sigue vigente lo que no contradice este ADR: hasta tres sesiones en paralelo, cada una en su *worktree*, y el escalamiento a la persona.
