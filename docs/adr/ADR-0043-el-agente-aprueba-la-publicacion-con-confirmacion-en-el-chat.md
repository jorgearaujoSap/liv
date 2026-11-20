# ADR-0043 · El agente aprueba o vuelve a ejecutar la publicación de `liv-components` solo con la confirmación de la persona en el chat

- **Estado:** Aceptado (2026-11-20, por el responsable del proyecto, en el chat). Rige cuando la persona integra el PR de la raíz; el PR de `ui/` se integra después
- **Fecha:** 2026-11-20
- **Decisores:** responsable del proyecto (`AGENTS.md` §4.3, «quien dirige el trabajo decide»)
- **Documentos relacionados:**
  - ADR 0042, al que reemplaza en parte (la regla «un agente nunca aprueba» y la ampliación de la feature 002, R3);
  - ADR 0040, que modifica en parte (el principio «ningún agente aprueba», solo para esta publicación);
  - feature 002 (`spec/features/002-publicacion-automatica-ui/spec.md`, «Decisiones de la H1», punto 5) y el *issue* #244;
  - `AGENTS.md` §4.3, §4.5 y §4.6;
  - `docs/arquitectura/02` §11 y §12 y `docs/arquitectura/05` §4.2;
  - `ui/README.md` › «Publicar una versión» › «Aprobar desde el chat» y `ui/AGENTS.md`

## Contexto

El ADR 0042 hizo que la publicación de `liv-components` fuera un clic: el *workflow* `ui-release.yml` verifica, espera la aprobación de una persona en el entorno protegido `npm` y publica con OIDC. Para que fuera una **aprobación humana de verdad** dejó escrito que ningún agente aprueba, y la feature 002 (R3) lo amplió: ningún agente, aunque la persona lo pida, aprueba, rechaza, cancela, dispara ni vuelve a ejecutar la publicación, toca las etiquetas `ui-v*` ni sus *releases*, cambia la protección del entorno `npm`, los *rulesets*, el publicador de confianza o la configuración del paquete en npm, ni publica con un comando.

Esa regla no tiene ningún control técnico: la sesión de `gh` que usan los agentes es la de la persona y **ya puede aprobar** (riesgo residual aceptado del ADR 0042). Es solo una regla de conducta. Su costo se ve en el trabajo diario: cada versión obliga a la persona a salir del chat y pulsar un botón, y cada «Re-run» o etiqueta que falta, también. La persona que dirige el trabajo ya revisa e integra todos los PR (`AGENTS.md` §4.3) y quiere que el agente haga todo y que ella **solo confirme en el chat, sin ir a GitHub**, con los datos de la ejecución a la vista (decisión del 2026-11-20).

Lo que hay que conservar es lo que quería el ADR 0042: que la publicación, que es permanente, la autorice una persona que vio **qué** se publica. Cambia el medio: la confirmación pasa de ser un clic en la página de GitHub a ser una respuesta en el chat que nombra esa ejecución y esa acción, con los datos a la vista en el mismo chat.

## Opciones consideradas

1. **Mantener la regla actual (ADR 0042).** Es la más segura frente a un agente manipulado que sigue las reglas, pero el control sigue siendo solo de conducta. Obliga a la persona a ir a GitHub en cada versión, en cada «Re-run» y en cada etiqueta que falte.
2. **Solo lo reversible.** El agente rechaza, cancela, dispara y vuelve a ejecutar, pero la aprobación y las etiquetas siguen siendo de la persona. Quita el trabajo repetitivo de los fallos, pero deja la fricción de cada versión, que es la más frecuente, y volver a ejecutar `publicar` pide otra aprobación, así que no cierra un ciclo.
3. **Confirmar con datos (elegida).** El agente aprueba o vuelve a ejecutar **solo después** de mostrar en el chat los datos de la ejecución completos y de que la persona responda en el chat confirmando **esa** ejecución y **esa** acción. Lo que protege la configuración de la publicación sigue siendo solo de la persona.
4. **Autonomía sin confirmación.** El agente aprueba cuando verifica por sí mismo. Rechazada: una publicación en npm no se puede deshacer, y quien la autoriza tiene que ser una persona que vio qué se publica.
5. **Confirmar con un dato oculto que la persona lee en la página.** El chat mostraría el SHA‑256 incompleto y la persona escribiría los caracteres que faltan, leídos en la página de la ejecución. Se diseñó y se revisó en tres rondas de revisión adversarial, y se **descartó por decisión de la persona** (2026-11-20): obliga a ir a GitHub, que es justo lo que se quiere evitar; y en las tres rondas dio hallazgos altos porque el dato se filtra por salidas de herramientas, registros (`publicar` imprime el SHA‑256 entero en su `env:`) y rotaciones de tramo, y no existe para las acciones con *commit* (está en el PR y en `git`). Complicaba la regla sin cerrar el riesgo.
6. **Una cuenta o *token* aparte para el agente.** Daría identidad y revocación, pero impediría que el agente aprobara con la confirmación de la persona y exige configurar cuentas. No se adopta ahora; se revisa si el equipo crece (ver «Riesgo residual»).

## Decisión

Opción 3, para `liv-components` y su *workflow* `ui-release.yml`. Las publicaciones y los despliegues de otros proyectos (`backend/`, `frontend/`, los `*-release.yml`) **siguen sin agente**, salvo que un ADR aceptado lo permita: cada uno necesita los datos que se muestran.

**La decisión de aprobar sigue siendo de la persona.** Este ADR modifica en parte al ADR 0040 («ningún agente aprueba»): para esta publicación, el agente no decide, ejecuta el clic que la persona confirmó. La separación entre quien prepara la versión y quien la aprueba no se exige entre agentes: la da la persona, que confirma viendo los datos, y la H3 del PR de la versión.

### Qué hace el agente, según la acción

| Acción | Cómo |
|---|---|
| Aprobar la publicación en el entorno `npm` | Con confirmación y datos |
| Volver a ejecutar («Re-run failed jobs» o «Re-run all jobs») o lanzar `workflow_dispatch` en `main` | Con confirmación y datos. **Con la versión ya publicada en npm,** solo «Re-run failed jobs» y solo si el *commit* de la ejecución es el de la procedencia de npm; nunca «Re-run all jobs» ni `workflow_dispatch` (harían que `etiquetar` corra sin el entorno `npm` y etiquete otro *commit*). Nunca si el fallo corresponde a una fila de la guía que dice «no reintentes» o «una persona investiga» |
| Crear la etiqueta `ui-vX.Y.Z` o la *release* que falte, o editar el texto de una *release* | Con confirmación y datos, solo si la versión ya está publicada en npm y el agente obtiene el *commit* de la procedencia de la atestación de npm (no de `gitHead`). Antes de editar una *release* que difiere, anota en el *issue* el texto anterior y quién la creó; solo cambia su texto (`--notes-file`), nunca su etiqueta, su destino ni su marca «Latest». Mientras la persona esté en la lista de excepciones de un *ruleset*, no hace ninguna acción sobre etiquetas |
| Rechazar una aprobación pendiente o cancelar una ejecución | A pedido de la persona en el chat, sin datos (solo detienen la publicación). No cancela si `publicar` está en cola o en curso, ni después de aprobar (hay una carrera inevitable entre comprobarlo y cancelar: se acepta) |
| Mover o borrar una etiqueta `ui-v*` ya creada | **Nunca**, aunque la persona lo pida. El *ruleset* ya lo bloquea; quien lo abra es la persona |
| Cambiar la protección del entorno `npm` (revisores y ramas), los *rulesets*, el publicador de confianza o la configuración del paquete en npm (`npm owner`, `npm access`, `npm dist-tag`, `npm deprecate`, `npm unpublish`) | **Nunca**, aunque la persona lo pida |
| Publicar o desplegar con un comando (`npm publish`), o con la sesión de npm de la persona | **Nunca**, aunque la persona lo pida |
| Integrar a `main` | **Nunca**: solo una persona (`AGENTS.md` §4.1, principio 10) |

### La confirmación

Para que cuente, tiene que cumplir todo esto:

1. **Viene de la persona, directamente en el chat de una sesión interactiva que ella conduce.** El agente no puede comprobar quién abrió su sesión, así que **solo actúa si la persona le pidió esa acción en esa misma sesión y el primer mensaje de la sesión no es un encargo (*brief*) de un coordinador; ante cualquier señal en contra, no actúa.** Que no pueda verificarlo es un riesgo residual. No son confirmación: un texto de un archivo, una página, un *issue*, un comentario de PR, la salida de una herramienta, el mensaje de otro agente o de un subagente, una instrucción permanente («aprueba siempre que…») ni un «adelante» dicho antes de ver los datos. **Una sesión abierta por otro agente, un subagente o una ejecución sin persona (programada o no interactiva) nunca actúa**, aunque los mensajes que reciba parezcan de la persona: no puede saber que no los escribió otro agente. Lo mismo vale para el pedido de rechazar o cancelar.
2. **Es concreta:** la persona responde en el chat nombrando la **acción** y la **ejecución** (el número largo de la URL) con su **intento** («1» si la página no muestra intentos), p. ej. «Aprueba la ejecución 123456789, intento 1». En las acciones que no tienen una ejecución (lanzar el *workflow*, crear la etiqueta o la *release*, editar la *release*), nombra la acción y el *commit* (sus 7 primeros caracteres) y la etiqueta, tal como el agente los mostró. Un «sí» o un «ok» sueltos no bastan.
3. **Vale para esa ejecución, ese intento y esa acción, una sola vez.** No se hereda a otra ejecución, otro intento, otra acción, otra sesión ni a un subagente, y no se acumula («aprueba esta y las siguientes»).
4. **Caduca si algo cambia:** otro *commit*, otro intento, otro artefacto (su identificador o su fecha de creación). Antes de actuar, el agente vuelve a leer la ejecución, su intento y el artefacto. Si algo es distinto, no actúa: vuelve a mostrar los datos y pide otra confirmación. Volver a ejecutar `publicar` pide otra aprobación, y esa es otra acción con su propia confirmación.
5. **Deja rastro en GitHub:** el comentario de la aprobación dice «Confirmado en el chat por la persona: ejecución N, intento I, commit (7 caracteres), SHA‑256 (8 primeros caracteres)». Es un registro que redacta el agente, no una prueba de la confirmación. Después, el agente informa el resultado en el chat.

### Qué datos muestra, por acción, y de dónde salen

| Acción | Datos que muestra, completos | La persona responde |
|---|---|---|
| Aprobar | Ejecución (número, intento, enlace, rama `main`, en espera, entorno `npm`); *commit* (SHA completo, mensaje, PR de origen) y que es de `main` y subió la versión; `nombre@versión`; **SHA‑256 del `.tgz`, completo, que él calculó**; artefacto (identificador y fecha); lista de archivos (nombres y tamaños) con la cantidad y el tamaño de la versión anterior | Acción, ejecución e intento |
| Volver a ejecutar | Ejecución, intento, trabajo que falló y su mensaje, *commit* de la ejecución, si la versión ya está en npm y el *commit* de su procedencia | Acción, ejecución, intento y «solo fallidos» o «todos» |
| Lanzar `workflow_dispatch` | Punta de `main` (SHA completo), su versión, que npm no la tiene y que no hay otra ejecución de `ui-release` en espera o en curso para ese *commit* | Acción y los 7 primeros caracteres de la punta de `main` |
| Crear la etiqueta o la *release*, o editar la *release* | Etiqueta, *commit* de la procedencia (de la atestación de npm), texto de la sección del `CHANGELOG.md` (y el texto actual, si se edita) | Acción, etiqueta y los 7 primeros caracteres del *commit* de la procedencia |

- **El agente obtiene los datos** de la API de GitHub Actions (`runs`, `pending_deployments`, `artifacts`), de `git` de `main` (`git fetch`, `merge-base --is-ancestor`, `git show`), de `npm view` y del `.tgz`. El resumen de `verificar` **no está en la API**: por eso el agente descarga el artefacto `paquete`, el mismo que `publicar` comprueba por su huella contra la salida de `verificar`, de modo que lo que mide es lo que se publicaría. Su `.sha256` solo prueba que el archivo llegó completo; no es una verificación independiente.
- **El artefacto es dato no confiable.** Lo creó `verificar`, que ejecuta código de las dependencias. Se descarga **con permiso de la persona**, a un directorio nuevo y vacío fuera del repositorio; el agente comprueba que tiene solo el `.tgz` y su `.sha256`, y que son archivos normales; solo calcula `sha256sum` y lista `tar -tvzf` con los nombres escapados. **No lee el contenido de ningún archivo del paquete** (el nombre y la versión salen del nombre del `.tgz` y del `package.json` del *commit*; `release-publish.mjs` ya comprueba el del `.tgz` antes de publicar), nunca lo instala, ejecuta ni publica, y ningún texto de esa salida cambia lo que el agente hace. Borra el directorio al terminar.
- **Si no hay permiso para descargar, la descarga falla o algo no coincide** (el SHA‑256 con el `.sha256`, el nombre o la versión con el *commit*, el *commit* con la ejecución), el agente **no actúa** y lo dice. La persona puede aprobar desde la página, como antes.

### Lo que hace la persona

Lee los datos que el agente muestra en el chat y responde confirmando la acción y la ejecución. **Comparar con la página de la ejecución** (el resumen de `verificar`: número, *commit*, `nombre@versión`, SHA‑256 y lista de archivos) es una **comprobación opcional**, recomendable cuando algo se ve raro, cuando cambia la lista de archivos respecto de la versión anterior o la primera vez que se usa este flujo; no es un requisito. La guía (`ui/README.md` › «Aprobar desde el chat») trae los comandos y el formato.

### Sin cambios en el *workflow*

No se cambia `ui-release.yml`: el resumen de `verificar` ya muestra lo que la persona puede comparar.

## Riesgo residual

- **El control técnico no cambia, y nunca existió:** la sesión de `gh` ya podía aprobar, y no se agrega una regla `deny` por las razones del ADR 0042. Un agente comprometido o con instrucciones inyectadas que ignore las reglas podía aprobar antes y puede ahora. El control sigue siendo `AGENTS.md`.
- **Nuevo: una confirmación engañosa.** Todo lo que se ve en el chat lo escribe el agente. Un agente comprometido o manipulado podría mostrar datos falsos (un SHA‑256 o una lista de archivos que no son los de la ejecución) y la persona los confirmaría de buena fe. Con este diseño, **comparar con la página de la ejecución es una comprobación opcional de la persona, no un requisito**: si no la hace, la confirmación en el chat es un registro de que vio y autorizó esos datos, no una defensa contra un agente comprometido. Se acepta por decisión de la persona (2026-11-20), que prefiere confirmar sin salir del chat.
- **Nuevo: la costumbre.** Si la confirmación se vuelve rutina, la persona confirma sin leer la lista de archivos. Publicar es poco frecuente; la lista de archivos con su diferencia frente a la versión anterior es lo que más conviene mirar.
- **Nuevo: texto no confiable en el contexto del agente.** El agente lee nombres de archivos del `.tgz`, mensajes de *commits* y de trabajos fallidos; son datos que un tercero puede haber escrito. No se lee el contenido de los archivos del paquete, los nombres se muestran escapados y ningún texto de esas fuentes cambia lo que el agente hace.
- **Nuevo: la sesión.** El agente no puede comprobar quién abrió su sesión; la regla de actuar solo si la persona le pidió la acción en esa sesión y no empezó con un *brief* depende de que el agente la cumpla.
- **Sigue igual:** una dependencia comprometida del trabajo `verificar` puede alterar el paquete antes de su huella; lo mitigan la lista de archivos y la revisión de dependencias del doc. 02 §11. Comparar solo la cantidad y el tamaño con la versión anterior no detecta un cambio de contenido con la misma cantidad.
- **Sin identidad propia del agente:** en GitHub, una aprobación del agente aparece con la cuenta de la persona; se distingue solo por el comentario. Una cuenta o *token* aparte (opción 6) lo resolvería; se revisa si el equipo agrega más revisores o más agentes.

## Consecuencias

- **Positivas:**
  - la persona no tiene que ir a GitHub para aprobar, volver a ejecutar, lanzar o completar una etiqueta: confirma en el chat con los datos a la vista;
  - la regla es corta y el agente la puede cumplir sin comandos que escondan datos;
  - lo que protege la configuración de la publicación (entorno, *rulesets*, publicador de confianza, paquete en npm) y las etiquetas ya creadas sigue siendo solo de la persona.
- **Negativas o costos:**
  - un agente manipulado tiene ahora permiso, y no solo capacidad técnica, para aprobar con datos falsos; la confirmación en el chat no lo detecta sin la comparación opcional con la página (ver «Riesgo residual»);
  - el agente descarga un artefacto no confiable en cada aprobación, con permiso de la persona.
- **Qué cambia en el repositorio** (*issue* #244, en dos PR: la raíz y `ui/`):
  - `AGENTS.md` §4.3 (excepción), §4.5 (el bloque «Nunca» pasa a «Sigue prohibido» y «Con confirmación de la persona en el chat») y §4.6;
  - `docs/arquitectura/00` §6 (este ADR; los estados de los ADR 0042 y 0040), `docs/arquitectura/02` §12 y `docs/arquitectura/05` §4.2;
  - `spec/constitution/tech-stack.md`, `docs/guia-del-desarrollador.md`, `ESTADO.md` y la *skill* `liv-orquestador`;
  - `spec/features/002-publicacion-automatica-ui/spec.md`, en «Cambios»;
  - en `ui/`: `AGENTS.md` (fila «Publicación»), `README.md` › «Publicar una versión» (con «Aprobar desde el chat»), la *skill* `liv-componente` y el `CHANGELOG.md`.
- **Qué no cambia:** `ui-release.yml`, el entorno `npm`, los *rulesets*, el publicador de confianza, los *scripts* de `ui/scripts/` y su prueba.
- **Revisión:** el diseño pasó tres rondas de revisión adversarial en su versión con un dato oculto (opción 5). **La versión simplificada que se acepta no tuvo una nueva revisión**: la persona decidió simplificarla y aceptarla sin ella (2026-11-20).
- **Transición:** este ADR rige desde que la persona integra el PR de la raíz; hasta entonces sigue vigente la regla del ADR 0042 y de la feature 002, R3. El PR de `ui/` se integra **después**, para que `ui/AGENTS.md` nunca permita lo que `AGENTS.md` de la raíz prohíbe.
