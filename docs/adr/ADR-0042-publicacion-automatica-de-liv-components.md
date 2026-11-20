# ADR-0042 · Publicación automática de `liv-components`: al integrar, con aprobación humana y sin clave guardada

- **Estado:** Aceptado (2026-11-19, en la H1 de la feature 002); reemplazado en parte por el ADR 0043, propuesto el 2026-11-20 y vigente desde que la persona lo acepte (un agente puede aprobar o volver a ejecutar la publicación con la confirmación de la persona en el chat)
- **Fecha:** 2026-11-18
- **Decisores:** responsable del proyecto (`AGENTS.md` §4.3, «quien dirige el trabajo decide»)
- **Documentos relacionados:**
  - feature 002 (`spec/features/002-publicacion-automatica-ui/spec.md`, con el diseño técnico completo);
  - ADR 0022, al que reemplaza en parte, y ADR 0040;
  - `docs/arquitectura/04` §9, `docs/arquitectura/01` §11.1 y `docs/arquitectura/02` §11 y §12;
  - `AGENTS.md` §4.5 y §4.6

## Contexto

Hoy publica a mano en npm una persona con cuenta y permiso, en seis pasos desde su equipo (ADR 0022: «un humano publica con `npm run release`»). El doc. 04 §9 preveía un `ui-release.yml` que se dispara al subir a mano la etiqueta `ui-vX.Y.Z` y que publica con un *token* guardado como secreto. Con las rebanadas verticales (ADR 0040), las publicaciones serán más frecuentes, y cada error es permanente: una versión publicada no se reemplaza.

## Opciones consideradas

1. **Seguir a mano.** No cambia nada, pero sigue dependiendo de seis pasos bien hechos en el equipo de cada persona.
2. **Lo previsto en el doc. 04 §9:** etiqueta subida a mano y *token* guardado como secreto. Agrega un paso manual con Git, y el *token* sirve hasta que alguien lo revoque.
3. **Al integrar, con aprobación y publicación de confianza.** El *workflow* detecta la versión nueva, verifica, espera la aprobación en un entorno protegido y publica con OIDC, sin clave guardada y con procedencia. Después etiqueta.

## Decisión

Opción 3.

**Disparo:**
- un `push` a `main` cuyo *commit* sube la versión de la librería por encima de la publicada, o `workflow_dispatch` en `main` con la misma regla. Solo publica el *commit* que subió la versión;
- la lógica de decisión vive en *scripts* con pruebas (P4).

**Aprobación humana:**
- se da en el entorno protegido `npm` de GitHub, que solo admite la rama `main`, con los revisores que designe el responsable;
- «publica un humano» pasa a ser **«un humano aprueba la publicación»**;
- un agente nunca la aprueba. Es un **riesgo residual aceptado**: los agentes usan la sesión de `gh` de la persona, que puede aprobar. No se agrega una regla `deny` en `.claude/settings.json`, porque se evitaría con `curl`, con el navegador o desde otros agentes. `AGENTS.md` §4.5 lo prohíbe de forma explícita para todo agente. El control real es la regla de `AGENTS.md`, y que la persona apruebe solo desde la página de la ejecución, viendo su resumen.

**Credencial:**
- publicación de confianza de npm (OIDC), con el *workflow* `ui-release.yml` **y el entorno `npm` exigidos** en la configuración de npm;
- el repositorio no guarda ninguna clave, y la publicación lleva procedencia;
- después de la primera publicación, npm exige 2FA y no admite *tokens*, y un *ruleset* protege las etiquetas `ui-v*` contra la actualización y el borrado, no contra la creación.

**Tres controles contra la publicación desde otra rama**, que valen aunque una rama del repositorio cambie el *workflow*:
1. npm exige el entorno `npm`;
2. el entorno solo admite `main`;
3. cada trabajo exige `github.ref == 'refs/heads/main'`.

**Separación de permisos:**
- el trabajo que publica no hace `checkout`, no instala las dependencias del proyecto y no ejecuta nada que haya pasado por un trabajo que las instaló: sus *scripts* vienen del trabajo que decide, que no instala nada;
- usa Node y npm en versiones exactas, sin caché, y publica con `--ignore-scripts` el paquete que construyó y verificó un trabajo anterior sin ese permiso;
- es idempotente: si la versión ya existe con la misma integridad, no la publica otra vez;
- solo el trabajo que etiqueta escribe en el repositorio, y etiqueta el *commit* de su propia ejecución.

**Riesgo residual aceptado:** una dependencia comprometida en el trabajo que construye puede alterar el paquete antes de su *hash*. Lo mitigan:
- la lista de archivos del paquete, que se muestra a quien aprueba;
- la revisión de dependencias del doc. 02 §11.

**Sin publicación a mano**, ni de emergencia: si GitHub Actions no está disponible, se espera. Hasta integrar el PR que activa la publicación automática, sigue la publicación a mano de hoy.

**Repositorio privado:** si el repositorio pasa a privado, este ADR se revisa y la publicación se detiene mientras tanto. La procedencia y la aprobación del entorno dependen de la visibilidad o del plan de GitHub.

## Consecuencias

- **Positivas:**
  - nadie necesita cuenta de npm para publicar;
  - publicar es un clic;
  - no hay claves que se puedan filtrar;
  - cada versión es verificable;
  - las rebanadas que necesitan un componente nuevo esperan minutos, no horas.
- **Negativas o costos:**
  - **configuración de la persona, en orden:**
    1. el entorno `npm` antes de la rebanada R2;
    2. el publicador de confianza antes de la R3;
    3. 2FA sin *tokens* y el *ruleset* de etiquetas después de la primera publicación;
  - aprobar o rechazar antes de integrar otra versión, porque GitHub deja una sola ejecución en espera;
  - el *workflow* es zona roja y lleva revisión adversarial en cada cambio.
- **Qué cambia en el repositorio:**
  - `.github/workflows/ui-release.yml` y el paso de `release:check --ci` en `ui-pr.yml`, con el comentario de ese archivo;
  - en `ui/`:
    - los *scripts* `release-decide`, `release-notes` y `release-publish`;
    - el modo `--ci` de `check-release`;
    - el campo `repository` del paquete;
    - `README.md` y `AGENTS.md`;
  - `docs/arquitectura/04` §9 (fila y diagrama), `docs/arquitectura/01` §11.1 (fila y diagrama) y `docs/arquitectura/02` §11;
  - `AGENTS.md` §4.5 (las líneas de publicar `liv-components` y de publicar versiones) y §4.6;
  - `spec/constitution/tech-stack.md`;
  - el doc. 00 §6, con este ADR y el ADR 0022 reemplazado en parte;
  - `ESTADO.md`.

  Las reglas y la guía cambian **antes** de activar la publicación real.
