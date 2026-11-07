# 002 · Publicación automática de liv-components

**Estado:** propuesta
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** ninguno de negocio; entrega de la librería `ui/` (Fase 0, F0‑07) · **Menú:** no aplica · **Tipo de cambio:** crítico (`.github/**` y `ui/**`, `AGENTS.md` §4.3)
**Fuentes:** `docs/arquitectura/04` §9 (fila `ui-release.yml`); `docs/arquitectura/01` §11.1 (fila «Distribución»); ADR 0022; `ui/README.md` › «Publicar una versión»; `AGENTS.md` §4.5 y §4.6

## Qué hace

Hoy, para publicar una versión de `liv-components`, alguien con cuenta y permiso en npm tiene que hacer varias cosas a mano:

1. integrar el PR;
2. actualizar `main` en su equipo;
3. `npm login` con verificación en dos pasos;
4. `npm run release`;
5. comprobar la publicación;
6. crear y subir la etiqueta `ui-vX.Y.Z`.

Con esta feature, quien trabaja en la librería solo prepara la versión en su PR, como hasta ahora: sube el número (`npm run version:patch`, `minor` o `major`) y escribe su sección en `CHANGELOG.md`. Al integrar el PR en `main` pasa esto:

1. GitHub detecta que el PR sube la versión de la librería por encima de la publicada.
2. Repite las verificaciones de hoy: *lint*, formato, pruebas, empaquetado, `CHANGELOG.md` y un número mayor que el publicado.
3. Espera a que el responsable apruebe la publicación con un clic en GitHub.
4. Al aprobarla, GitHub publica la versión en npm, crea la etiqueta `ui-vX.Y.Z` y una *release* con lo que dice el `CHANGELOG.md`.

Nadie necesita cuenta de npm ni ejecutar comandos para publicar. El paquete lleva su procedencia verificable: npm muestra que lo construyó este repositorio desde ese *commit*.

## Por qué

- **Lo pidió el responsable:** quien trabajará en las features tiene conocimientos básicos, y publicar a mano en npm es complejo y fácil de hacer mal.
- **Un error no tiene arreglo:** una versión publicada no se puede reemplazar. Hoy depende de que cada persona siga bien los seis pasos en su equipo.
- **Menos riesgo:**
  - el repositorio no guarda ninguna clave de npm. GitHub obtiene un permiso que dura minutos y solo sirve para publicar este paquete desde este *workflow*. Una clave guardada se puede filtrar y sirve hasta que alguien la revoque;
  - el paso que publica no instala dependencias, así que una dependencia comprometida no puede publicar.
- **Ya estaba previsto:** `docs/arquitectura/04` §9 prevé `ui-release.yml` con aprobación manual, y F0‑07 está en curso.

## Criterios de aceptación

- [ ] **002-01** — Dado un PR que sube la versión de la librería, cuando se integra en `main`, entonces se ejecutan las verificaciones de publicación y la publicación queda **esperando la aprobación** del responsable. Las verificaciones son las de `npm run release:check` (sección del `CHANGELOG.md` y número mayor que el publicado), *lint*, formato, pruebas y empaquetado. _Prueba:_ pendiente
- [ ] **002-02** — Dado que la publicación espera la aprobación, cuando el responsable la aprueba, entonces la versión queda publicada en npm con su procedencia verificable (`npm audit signatures`). El repositorio no guarda ninguna clave de npm. _Prueba:_ pendiente
- [ ] **002-03** — Dada una versión aprobada, cuando termina su publicación, entonces existe la etiqueta `ui-vX.Y.Z` en el *commit* publicado y una *release* de GitHub con la sección de esa versión del `CHANGELOG.md`. _Prueba:_ pendiente
- [ ] **002-04** — Dado un PR que sube la versión, cuando falla una de las verificaciones de 002-01, entonces no se pide la aprobación, no se publica ni se etiqueta. El registro dice qué falló y cómo corregirlo, con los mensajes de `ui/scripts/check-release.mjs` para sus verificaciones. _Prueba:_ pendiente
- [ ] **002-05** — Dado un cambio que no sube la versión de la librería, cuando se integra en `main`, entonces no se pide la aprobación ni se publica. _Prueba:_ pendiente
- [ ] **002-06** — Dada una versión ya publicada, cuando se vuelve a ejecutar la publicación (p. ej. con «Re-run»), entonces no se intenta publicarla otra vez: solo se crea la etiqueta o la *release* que falte. _Prueba:_ pendiente
- [ ] **002-07** — Dado un PR, una rama distinta de `main` o un *fork*, cuando corre su CI, entonces nunca se publica. La aprobación solo existe para ejecuciones de `main`, y solo la pueden dar las personas que el responsable designe. _Prueba:_ pendiente
- [ ] **002-08** — Dado el paso que tiene el permiso de publicar, cuando se ejecuta, entonces no instala ni ejecuta las dependencias del proyecto: publica solo el paquete construido y verificado por un paso anterior que no tiene ese permiso. _Prueba:_ pendiente
- [ ] **002-09** — Dada la guía de `ui/README.md` › «Publicar una versión», cuando se lee, entonces tiene pasos numerados para cuatro cosas: preparar la versión en el PR, aprobar la publicación (quién y dónde), resolver cada fallo de 002-04 y adoptar la versión en el frontend. La publicación a mano aparece solo en una sección aparte de emergencia. _Prueba:_ pendiente

## Fuera de alcance

- **Adoptar la versión en el frontend:** sigue siendo un PR propio de `frontend/`, que cambia el número y sube `package.json` y `package-lock.json` (`AGENTS.md` §4.6). Que un bot abra ese PR (Dependabot o Renovate) se puede evaluar después, como otra feature.
- **Versiones preliminares** (`-beta`, etiqueta `next` de npm), retirar versiones (`npm unpublish`) y marcarlas obsoletas (`npm deprecate`).
- **Publicar el catálogo de Storybook** y las capturas de referencia de regresión visual (Q14).
- **Volver obligatorio el *check* `ui · verify`:** sigue pendiente en F0‑07, con los demás *checks*.

## Preguntas abiertas

Estas decisiones cambian los criterios, así que se resuelven en la aprobación H1:

- **Qué dispara la publicación.**
  - **Propuesta:** integrar en `main` un PR que suba la versión; GitHub espera la aprobación y luego crea la etiqueta.
  - **Alternativa:** lo que dice hoy `docs/arquitectura/04` §9, que dispara la publicación al subir a mano la etiqueta `ui-vX.Y.Z`. Agrega un paso manual con comandos de Git.
  - Con la propuesta, «publica un humano» (`AGENTS.md` §4.6) pasa a ser «un humano aprueba la publicación».
  - Es una decisión de arquitectura: va en el ADR 0031, que actualiza `docs/arquitectura/04` §9, `docs/arquitectura/01` §11.1 y `AGENTS.md` §4.5 y §4.6. Todos ellos necesitan la aprobación del responsable.
- **La credencial.**
  - **Propuesta:** publicación de confianza de npm (*trusted publishing*): GitHub se identifica ante npm sin una clave guardada.
  - **Alternativa:** lo que dice hoy `docs/arquitectura/04` §9, un *token* guardado como secreto.
  - También va en el ADR 0031.
- **Configuración que solo puede hacer el responsable.** Los dos pasos se hacen una sola vez. El plan los detalla paso a paso.
  - En npmjs.com: registrar este repositorio y este *workflow* como publicadores de confianza de `liv-components`.
  - En GitHub: crear el entorno protegido `npm`, con el responsable como aprobador.
- **¿Se deja la publicación a mano como alternativa de emergencia?**
  - **Propuesta:** sí, con verificación en dos pasos y solo si GitHub Actions no está disponible.
  - **Alternativa:** prohibir en npm las claves y la publicación fuera del *workflow*. Es más seguro, pero si GitHub falla no hay cómo publicar.
