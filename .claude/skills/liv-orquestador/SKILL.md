---
name: liv-orquestador
description: Coordina el backlog de la feature en construcción del proyecto LIV (ADR 0040) - propone la siguiente rebanada, la prepara, la reparte en hasta tres sesiones nuevas (diseño, backend y frontend) con el modelo de cada nivel, revisa lo que entregan, lleva la deuda y cierra la feature. Úsala cuando la persona quiera avanzar varias rebanadas a la vez, «orquestar», «coordinar», «repartir el trabajo» o continuar la feature en curso.
---

# Coordinador del backlog de LIV

El coordinador mueve **el backlog de la feature en construcción**: decide qué rebanada sigue, quién la hace y qué corre en paralelo; revisa y cierra. **No construye las rebanadas**: las delega en sesiones nuevas y comprueba que lleguen bien hechas. Es opcional: una rebanada también se puede hacer en una sola sesión con `liv-sdd-feature`.

Reglas que no cambian:
- **Una sola feature en construcción a la vez** (ADR 0040). La siguiente puede preparar su arranque mientras tanto.
- **La persona** aprueba la spec (H1), da el visto bueno de cada rebanada en el chat (el coordinador la mueve a «Lista»), hace la H3 de **cada** PR, integra cada PR y decide todo lo de `AGENTS.md` §4.5.
- El coordinador y sus sesiones **nunca**:
  - integran un PR de ninguna forma (`gh pr merge`, tampoco con `--auto`);
  - aprueban un PR (`gh pr review --approve`);
  - cierran un PR o borran una rama sin que la persona lo pida;
  - cambian la protección de `main` o sus *rulesets*;
  - publican, despliegan o aceptan un ADR.
- El coordinador corre con el modelo de **nivel alto** (`AGENTS.md` §10). El estado pasa por el repositorio y por GitHub (`spec.md`, los *issues*, los PR y el *Project*), no por el historial: si la sesión se reinicia, retoma desde §0.

## 0. Antes de empezar

1. Lee `ESTADO.md` (feature en construcción, próximos pasos, bitácora reciente) y el `spec.md` de la feature.
2. Mira el backlog: `gh issue list --label feature:NNN` y el *Project*. Mira lo que está en marcha de verdad: PR abiertos (`gh pr list`), ramas, *worktrees* y sesiones.
3. Si no hay feature en construcción, propónle a la persona la siguiente según el roadmap y **espera a que la elija**. El arranque se hace con `liv-sdd-feature` §1.

## 1. Qué rebanada sigue

En el orden de la tabla «Rebanadas» de `spec.md`, salvo que la persona diga otra cosa:
- R1, el esqueleto, va siempre primero y sola.
- Después, primero lo más incierto, para que los errores salgan pronto.
- Una rebanada empieza solo en «Lista». Si una está lista para preparar, complétale el *issue* (definición de lista, `AGENTS.md` §4.2; con el nivel alto si toca una zona roja) y pídele el visto bueno en el chat. Con él, muévela tú a «Lista» y anótalo en el *issue* (`liv-backlog` §4). Sin él, nunca.

## 2. Qué corre en paralelo

Como máximo **tres sesiones de trabajo a la vez**; los subagentes de revisión no cuentan. Un reparto típico:

| Carril | Qué hace | Cuándo puede empezar |
|---|---|---|
| Diseño | El diseño en Figma de la rebanada **siguiente** (`liv-figma`) | Cuando la rebanada actual ya tiene el suyo |
| Backend | El contrato y el backend de la rebanada actual | Cuando la rebanada está en «Lista» |
| Frontend | La pantalla de la rebanada actual, contra el contrato | Cuando el contrato de la rebanada está escrito en su rama; su PR se integra después de la *release* del contrato |

Antes de lanzar una sesión, tres comprobaciones:
1. **¿Depende de algo sin integrar?**
   - El frontend necesita el contrato.
   - Un componente nuevo de `liv-components` tiene que estar **publicado en npm**: lo publica el *workflow* `ui-release.yml` al integrar el PR que sube la versión, y **solo la persona aprueba la publicación** (hasta que se active, la publica a mano). Un agente nunca la aprueba (`AGENTS.md` §4.5).

   Si depende de algo sin integrar, espera.
2. **¿Comparte un recurso con algo en curso?**
   - Una sola sesión por proyecto.
   - Las migraciones se integran en el orden de su versión.
   - Si dos PR chocan en el `CHANGELOG.md`, el que se integra después se rebasa.
3. **¿Necesita un recurso exclusivo?**
   - Son exclusivos el entorno local de `tools/dev/`, las pruebas de navegador de `e2e/` y Figma.
   - Testcontainers no cuenta como exclusivo.

## 3. Lanzar una sesión: el *brief*

Cada sesión arranca sin la memoria del coordinador. Su *brief* lleva:
1. **Qué:** el *issue* de la rebanada (`#n`), el proyecto y la rama (`feat/<NNN>-r<n>-<proyecto>`).
2. **Dónde leer:** `spec.md`, el *issue*, el `AGENTS.md` del proyecto y su *skill* (`liv-endpoint`, `liv-migracion`, `liv-pantalla`, `liv-componente` o `liv-prueba-e2e`).
3. **Cómo:** `liv-sdd-feature` §3. Si toca una zona roja, una regla con código o un documento funcional, pasa la revisión adversarial. Los *issues* nuevos que encuentre se los propone a la persona (`liv-backlog` §3). No toca `ESTADO.md`, `spec.md` ni el glosario.
4. **Cuándo termina:** con el PR abierto, el CI en verde, la revisión adversarial hecha si correspondía y un reporte que dice qué hizo, qué falló, qué no verificó y qué *issues* propone.

**Cómo se lanza:** siempre en una **sesión nueva e independiente**, en su propio *worktree* y con el modelo de su nivel (estándar para construir). Nunca en un subagente.
- En la aplicación de escritorio de Claude Code, con la herramienta de sesiones (`start_session`, `CLAUDE.md`).
- Si no hay esa herramienta, o no permite fijar el modelo o el *worktree*, el coordinador le da el *brief* a la persona.

## 4. Revisar lo que entregan

- El reporte de una sesión **no es una prueba**: comprueba el PR, el *diff* y el CI.
- Si una sesión pregunta algo que `spec.md` y el *issue* resuelven, respóndele. Si no, escálalo (§5).
- **Una sesión sin terminar** es la que se detuvo por un error o lleva más de 24 horas sin *commits* ni reporte. Libera el entorno local si lo tenía (`tools/dev/detener.sh`), y propónle a la persona reanudarla (sesión nueva, mismo *brief*, misma rama) o descartarla.
- Cuando todos los PR de una rebanada están integrados, comprueba que funcione de punta a punta en local y cierra el *issue*.
- Junta los *issues* que proponen las sesiones en una sola bandeja para la persona (`liv-backlog` §3), sin duplicados.
- Lleva la cuenta de la **deuda**: con más de cinco *issues* `deuda` abiertos en el repositorio, la siguiente rebanada es de limpieza.
- Los cambios de criterios, de diseño técnico o de rebanadas con zona roja que la persona aprobó van a `spec.md` en un PR pequeño de la raíz, que preparas tú. Los cambios menores y los términos del glosario quedan en los *issues* y entran en el PR de cierre.

## 5. Escalar a la persona

Siempre consulta a la persona cuando:
- hay que elegir o arrancar una feature, o dar el visto bueno para mover una rebanada a «Lista»;
- hace falta una H1 o una H3, o algo de `AGENTS.md` §4.5;
- una rebanada necesita cambiar el diseño técnico de `spec.md`, una regla, o algo de una zona roja que la spec no prevé;
- aparece un `PENDIENTE` o `POR_CONFIRMAR` funcional;
- un revisor deja un hallazgo crítico o alto sin resolver;
- el CI falla dos veces por la misma causa;
- la deuda pasa del tope.

Júntalo en una **bandeja de decisiones**: un mensaje corto, con cada punto en una línea que dice qué hay que decidir, por qué y su recomendación. Lo que la spec y los *issues* ya resuelven, decídelo sin preguntar. Si la persona no está de acuerdo, **prevalece la persona**.

## 6. Cerrar la feature

Cuando todas las rebanadas están hechas, sigue `liv-sdd-feature` §4: pruebas de navegador, criterios marcados, deuda revisada, menú abierto y PR de cierre con el roadmap, el glosario, los ADR y `ESTADO.md`. La siguiente feature empieza solo cuando la persona la elige.
