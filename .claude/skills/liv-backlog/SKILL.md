---
name: liv-backlog
description: Registra y mantiene el backlog del proyecto LIV en GitHub Issues y su Project (ADR 0040) - crea los issues de las rebanadas de una feature aprobada, propone a la persona los issues nuevos que el agente encuentra (defectos, mejoras, deuda, rebanadas sueltas) y los crea solo con su aprobación, evita duplicados, enlaza los PR y mueve las tarjetas. Úsala al armar el backlog de una feature, cuando aparezca algo que arreglar, mejorar o pagar fuera de la rebanada en curso, o cuando la persona pida "registrar un issue", "ver el backlog" o "qué hay pendiente".
---

# Backlog de LIV en GitHub

El trabajo se sigue en *issues* de GitHub y en el *Project* del repositorio (ADR 0040, `AGENTS.md` §4.2). Esta *skill* dice cómo se crean, cómo se proponen y quién decide.

**Regla de oro** (ADR 0041): **el agente mantiene el tablero completo.**
- **Lo trivial y reversible lo hace sin preguntar:** crear las rebanadas de una spec aprobada, mover las tarjetas según el trabajo real, enlazar PR, comentar, etiquetar, llenar campos, completar los *issues* y corregir el tablero.
- **Lo que es una decisión de la persona lo pide en el chat** y lo ejecuta en cuanto ella responde: mover una rebanada a *Ready* («Lista»), crear un *issue* nuevo que encontró, cerrar sin terminar, borrar o reabrir un *issue*, y cambiar la configuración del tablero.
- **Criterio:** si decide qué se construye, qué entra al backlog o cómo funciona el tablero, se pregunta; si solo refleja lo que ya pasó o lo ya aprobado, se hace.

## 0. El tablero y las etiquetas

**El tablero** es el *Project* **`liv-project`** de la cuenta `jorgearaujoSap` (número 1, privado), creado con la plantilla Kanban. Cambiar sus columnas, campos o automatizaciones lo hace el agente con la autorización de la persona en el chat.

| Columna del tablero | En las reglas (`AGENTS.md` §4.2) | Quién la mueve |
|---|---|---|
| *Backlog* | Backlog | Entra sola al crear el *issue* («Item added to project»), o la pone el agente |
| *Ready* | «Lista» | El agente, **con el visto bueno de la persona en el chat**, que anota en un comentario del *issue* con la fecha |
| *In progress* | «En curso» | El agente, al empezar la rebanada; o sola, al enlazar un PR («Pull request linked to issue») |
| *In review* | «En revisión» | El agente, al abrir el PR |
| *Done* | «Hecha» | Sola, al cerrarse el *issue* («Item closed»), o el agente si la automatización no lo hizo |

Los campos *Priority* (P0 a P2) y *Size* (XS a XL) de la plantilla son opcionales. Si la persona los usa, *Size* va así: XS o S es un ajuste, M una rebanada y L o XL una rebanada que conviene partir.

**Las etiquetas** ya existen:
- `rebanada`, `deuda` y `zona-roja`;
- `feature:NNN`, una por feature. Créala al arrancar cada una:

  ```bash
  gh label create feature:NNN --color C5DEF5 --description "Feature NNN · <nombre>"
  ```
- **Defectos y mejoras:** se usan las de GitHub, `bug` y `enhancement`.

**Cuenta de GitHub:** el tablero y el repositorio son de `jorgearaujoSap`. Si `gh` responde con un error de permisos, revisa con qué cuenta está trabajando: el repositorio puede indicar la suya en `git config gh.account`.

## 1. Tipos de *issue*

| Tipo | Etiquetas | Título | Cuándo |
|---|---|---|---|
| Rebanada de una feature | `rebanada`, `feature:NNN` (+ `zona-roja`) | `NNN · R<n> · <qué se podrá hacer>` | Al armar el backlog de una spec aprobada |
| Rebanada suelta | `rebanada` (+ `zona-roja`) y `bug` o `enhancement` | `<módulo> · <qué se podrá hacer>` | Un defecto en una zona roja o en una regla, algo de «Después», una mejora acotada |
| Deuda | `deuda` (+ `zona-roja`) | `deuda · <módulo> · <qué se dejó>` | Un atajo de una rebanada, o deuda que se encuentra en el código |
| Defecto o mejora pequeña | `bug` o `enhancement` | `<módulo> · <qué pasa>` | Algo que cabe en un ajuste (`AGENTS.md` §4.3), para no olvidarlo si no se hace ya |

**Cuerpo de una rebanada:** el formato de `spec/README.md` › «Formato del *issue* de una rebanada».

**Cuerpo de una deuda:**

```markdown
**Qué se dejó:** <el atajo, con ruta:línea>
**Por qué:** <qué lo justificó en su momento>
**Riesgo si no se paga:** <qué puede salir mal y cuándo>
**Cómo se paga:** <el cambio, y su tamaño: ajuste o rebanada>
**Origen:** <PR #n o rebanada #n>
```

**Cuerpo de un defecto o una mejora:**

```markdown
**Qué pasa:** <comportamiento actual, con cómo reproducirlo si es un defecto>
**Qué debería pasar:** <comportamiento esperado, con la regla o el criterio si hay uno>
**Dónde:** <ruta:línea, pantalla o endpoint>
**Tamaño propuesto:** <ajuste | rebanada suelta | parte de la feature NNN>
```

Nunca pongas en un *issue* datos reales de contribuyentes, credenciales ni contenido de los archivos del banco.

## 2. Armar el backlog de una feature aprobada

Las rebanadas de una spec aprobada en la H1 ya tienen la aprobación de la persona, así que se crean sin preguntar de nuevo:

1. Por cada fila de la tabla «Rebanadas» de `spec.md`, crea el *issue*:

   ```bash
   gh issue create --title "NNN · R1 · <…>" --label rebanada --label feature:NNN --body-file <archivo>
   ```

2. Agrégalo al *Project* en «Backlog»:

   ```bash
   gh project item-add 1 --owner jorgearaujoSap --url <url del issue>
   ```

3. Anota el número de cada *issue* en la tabla de `spec.md`, dentro del PR de arranque.
4. Lo que quedó en «Después» se **propone** como rebanadas sueltas (§3): no entra solo.

## 3. Proponer un *issue* que encontraste

Mientras trabajas puede aparecer algo fuera de la rebanada en curso: un defecto, una mejora, una deuda vieja, un atajo propio o algo que falta en la spec. **No lo arregles por tu cuenta ni lo crees directamente:**

1. **Busca si ya existe:**

   ```bash
   gh issue list --state all --search "<palabras clave>"
   ```

   Si ya existe, propón agregarle un comentario en lugar de duplicarlo.
2. **Clasifícalo:** tipo (§1), tamaño (`AGENTS.md` §4.3) y si toca una zona roja.
3. **Júntalo en una bandeja** para la persona, al final de tu trabajo o en una pausa natural, no a mitad de una tarea. Una línea por *issue*:

   > **Issues propuestos**
   > 1. `deuda · tax · el redondeo se calcula dos veces` — deuda, zona roja; sale del PR #231; propongo pagarlo en la siguiente rebanada.
   > 2. `frontend · el filtro no se borra al volver` — defecto, ajuste.
   >
   > ¿Cuáles creo?

4. **Crea solo los que la persona apruebe**, con su cuerpo completo (§1), en «Backlog». Si rechaza la deuda de un atajo tuyo, el atajo se corrige en la misma rebanada (`AGENTS.md` §4.4).
5. Si la persona dice que algo se arregle ya y cabe en un ajuste, no hace falta *issue*: va directo a su PR.

## 4. Mantener el tablero

- **Los PR enlazan su *issue*:** usa `Parte de #n` en los PR intermedios y `Closes #n` solo en el último de la rebanada. GitHub cierra el *issue* al integrar ese PR; solo reconoce las palabras en inglés (`Closes`, `Fixes`, `Resolves`), así que «Cierra #n» no sirve.
- **Mover las tarjetas** con `gh project item-edit`, con los identificadores que da `gh project field-list 1 --owner jorgearaujoSap`:
  - a *In progress* al empezar;
  - a *In review* al abrir el PR;
  - a *Done* si la automatización no lo hizo al cerrarse el *issue*.
- **A *Ready*:** cuando la persona da el visto bueno en el chat, para una o varias rebanadas. Antes, comprueba la definición de lista (`AGENTS.md` §4.2). Al moverla, deja un comentario en el *issue*: «Lista: visto bueno de la persona el AAAA-MM-DD».
- **Al empezar cada sesión:** compara el tablero con los PR reales (`gh pr list --state all`) y corrige las tarjetas atrasadas.
- **Solo *issues*:** las tarjetas son las rebanadas y sus PR las enlazan. Si un PR aparece como tarjeta, sácalo del tablero sin cerrarlo (`gh project item-delete`): es trivial.
- **Deuda:** antes de proponer una rebanada nueva, cuenta los *issues* `deuda` abiertos:

  ```bash
  gh issue list --label deuda --state open --json number --jq length
  ```

  Si son más de cinco, avísale a la persona: la siguiente rebanada es de limpieza.
- **Ver el backlog:** `gh issue list --label feature:NNN`, `gh issue list --label deuda` y `gh project item-list 1 --owner jorgearaujoSap`. Resúmeselo a la persona en una tabla corta: número, título, estado y tipo.

## Qué no hacer

- No crear *issues* nuevos sin la aprobación de la persona, salvo las rebanadas de una spec aprobada.
- No mover nada a *Ready* («Lista») sin su visto bueno en el chat.
- No cerrar sin terminar, borrar ni reabrir *issues* sin su autorización. Un duplicado se cierra como duplicado, con el enlace al original, y solo con su visto bueno.
- No usar los *issues* para guardar decisiones de arquitectura: van en un ADR (`liv-adr`). Tampoco para las reglas de negocio: van en los documentos funcionales.
