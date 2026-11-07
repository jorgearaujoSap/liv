# 003 · CI obligatorio en main

**Estado:** propuesta
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** ninguno de negocio; CI del repositorio (Fase 0, F0‑07) · **Menú:** no aplica · **Tipo de cambio:** crítico (`.github/**`, `AGENTS.md` §4.3)
**Fuentes:** ADR 0031 (criterio de cierre de la Fase 0, condición 2); `docs/arquitectura/04` §9 (compuertas y GitHub Actions); `ESTADO.md` §3, F0‑07

## Qué hace

Hoy un PR se puede integrar en `main` aunque el CI de su proyecto esté en rojo. Solo son obligatorios los dos *checks* de `sistema` (título y secretos). Los de cada proyecto no lo son porque sus *workflows* corren solo cuando el PR toca ese proyecto: un *check* obligatorio que nunca corre deja el PR esperando para siempre.

Con esta feature:
- cada *workflow* de PR corre en **todos** los PR;
- un primer paso mira qué archivos cambia el PR. La verificación de un proyecto solo se omite si **todos** los archivos del PR están en lugares que se sabe que no lo afectan: otros proyectos o la documentación. GitHub cuenta un trabajo omitido como aprobado;
- en cualquier otro caso la verificación corre, y si falla, el PR **no se puede integrar**;
- la protección de `main` exige el *check* de cada proyecto.

## Por qué

- Es la condición 2 del cierre de la Fase 0 (ADR 0031).
- El desarrollador que reciba el proyecto no revisará el código a fondo. Que la máquina impida integrar algo en rojo es la barrera más importante (`AGENTS.md` §4.1, principio 3: «la máquina verifica, el humano decide»).

## Criterios de aceptación

- [ ] **003-01** — Dado un PR que toca un proyecto (`backend/` fuera de `backend/bff/`, `backend/bff/`, `frontend/` o `ui/`), cuando corre el CI, entonces corre la verificación de ese proyecto, y si falla, el PR no se puede integrar en `main`. También cuando un archivo sale del proyecto (se mueve o se borra). _Prueba:_ pendiente
- [ ] **003-02** — Dado un PR cuyos archivos están todos en otros proyectos o en lugares que no afectan a ningún proyecto (`docs/`, `spec/`, `e2e/`, `tools/`, `.claude/`, los `.md` de la raíz, `.gitignore` y `.mcp.json`), cuando corre el CI, entonces la verificación de ese proyecto aparece como omitida y no impide integrar el PR. _Prueba:_ pendiente
- [ ] **003-03** — Dado un PR que cambia los contratos OpenAPI, el `pom.xml` o el *wrapper* de Maven del backend o del BFF, cuando corre el CI, entonces corren el comparador de cambios incompatibles (Q08) y sus pruebas, y si alguno falla, el PR no se puede integrar. _Prueba:_ pendiente
- [ ] **003-04** — Dado un PR que cambia un archivo de `.github/`, otro archivo de la raíz que afecta a todo el repositorio (como `.gitattributes`) o cualquier ruta que no esté en la lista conocida, cuando corre el CI, entonces corren las verificaciones de **todos** los proyectos. _Prueba:_ pendiente
- [ ] **003-05** — Dado que no se puede saber qué archivos cambia el PR (falla la consulta o la respuesta no se entiende), o que alguien cancela la ejecución, cuando corre el CI, entonces ninguna verificación queda omitida: corren todas, o el *check* queda en rojo. Ante la duda no se omite nada. _Prueba:_ pendiente
- [ ] **003-06** — Dada la protección de `main`, entonces exige el título del PR, los secretos, la verificación de cada proyecto y los contratos. Nadie tiene permiso para saltársela. Un PR con cualquiera de ellos en rojo, o sin terminar, no se puede integrar. _Prueba:_ pendiente
- [ ] **003-07** — Dado el cambio, entonces no agrega acciones de terceros ni otras dependencias: solo usa lo que ya hay en el repositorio y en los ejecutores de GitHub (`git`, `bash`). _Prueba:_ pendiente

## Fuera de alcance

- Las demás compuertas de F0‑07: cobertura, mutación, Sonar, NullAway, Stylelint y guardián SDD (ADR 0031, no bloquean el cierre).
- Las pruebas de navegador en CI (`e2e/`), que van con T24 (ADR 0030). Hasta entonces, un PR que solo toca `e2e/`, `tools/`, `spec/` o `docs/` solo tiene los *checks* de `sistema`.
- La publicación al integrar (`backend-main`), que no es un *check* de PR y sigue con su filtro de rutas.
- Exigir aprobaciones de revisores: se activa al sumarse una segunda persona (`AGENTS.md` §4.3, excepción del equipo de una persona).
- Impedir que un PR desactive su propia verificación cambiando el *workflow*. Corre el *workflow* que trae el PR, y la protección solo mira el nombre del *check*. Queda como riesgo declarado en el plan: la única barrera es la revisión humana (H3) de todo cambio en `.github/`.

## Preguntas abiertas

- Ninguna.

## Decisiones tomadas en la H1

- **La rama no tiene que estar al día con `main` para integrarse** (decisión del responsable, 2026-11-07). Se trabaja como hoy. Se acepta el riesgo de que dos PR en verde por separado dejen `main` en rojo al integrarse uno detrás del otro.
