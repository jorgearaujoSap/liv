# ADR-0030 · Pruebas de navegador (`e2e/`) contra el entorno local hasta antes de producción

- **Estado:** Aceptado
- **Fecha:** 2026-11-06
- **Decisores:** responsable del proyecto, que lo aprobó en la sesión del 2026-11-06 al revisar el alcance de T23 de la feature 001 (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** ADR 0023 (dependencias de `e2e/`), ADR 0028; `docs/arquitectura/01` §14.1 (IN‑09); `docs/arquitectura/00` §8.1 (F0‑12); `docs/arquitectura/04` §5; feature 001, tarea T23; *skill* `liv-sdd-feature`

## Contexto

La regla IN‑09 dice que `e2e/`, `perf/` e `infra/` usan los proyectos **solo como imágenes publicadas y por HTTP**. La tarea T23 de la feature 001 la aplicaba completa. Para la primera prueba de navegador hacían falta:

- los `Dockerfile` de la API, el BFF y sus migraciones;
- el CI que construye, revisa con Trivy y publica las imágenes, y un registro de imágenes;
- un *compose* de `infra/` con Caddy y TLS en `liv.test` y `auth.liv.test`;
- el CI de e2e.

Son unos 8 a 10 cambios antes de la primera prueba, cuando el proyecto todavía no tiene features de negocio y nada se despliega. Pesan el costo y la velocidad de un equipo de una persona; ningún impulsor de doc. 00 §2 pide hoy esas pruebas en CI.

El responsable no quiere perder la verificación en el navegador de cada feature: que funcione y que se vea como el diseño. Pero no quiere ahora imágenes, `Dockerfile` ni CI.

## Opciones consideradas

1. **Cumplir IN‑09 desde ya.**
   - A favor: prueba el sistema como en producción.
   - En contra: un costo alto que no aporta mientras no haya despliegue, y retrasa el cierre de la feature 001.
2. **Diferir todo el e2e hasta antes de producción.**
   - A favor: lo más barato.
   - En contra: no hay ninguna verificación automática en el navegador. Cada feature dependería de una prueba manual.
3. **Pruebas de navegador contra el entorno local de desarrollo.**
   - Qué es: Playwright en `e2e/` contra el sistema levantado como dice `README.md` › «Ejecutar LIV en local», sin imágenes, CI ni TLS. IN‑09 completa se aplica antes de producción.
   - A favor: cada feature se verifica en el navegador desde ya, con funcionalidad, capturas de referencia y accesibilidad.
   - En contra: no prueba las cookies con TLS ni las imágenes que se desplegarán, y corre a mano, no en cada PR.

## Decisión

Se elige la opción 3.

- **Dónde y contra qué:** `e2e/` es un proyecto propio con su `package.json`, sin herramientas compartidas con otros proyectos. Corre contra `http://localhost:4200` del entorno local: `backend/dev`, la API y el BFF con `mvnw` y el frontend con `npm start`. Usa los proyectos solo por HTTP, nunca sus archivos.
- **Qué comprueba en cada feature con interfaz:**
  - los flujos de sus criterios, como los recorre una persona;
  - capturas comparadas contra una referencia que aprueba el responsable;
  - axe en cada pantalla;
  - las medidas del diseño que importan.
- **Cómo se usa:** las pruebas se escriben primero, en rojo, con la feature. El agente las corre al validar la feature y pone el resultado en el PR, y además revisa la pantalla en el navegador contra el diseño de Figma (*skill* `liv-sdd-feature`, paso 7). No hay CI de e2e por ahora.
- **Dependencias:** `@playwright/test` y `@axe-core/playwright`, ya aprobadas en el ADR 0023 para `e2e/`. `otplib`, aprobada también allí, no hizo falta: el código TOTP se calcula con `node:crypto` de Node (RFC 6238).
- **Datos de prueba:** los usuarios ficticios de `backend/dev/keycloak/`. Los que necesitan un TOTP conocido o se bloquean los crean las pruebas al empezar, con la API de administración de Keycloak del entorno local, y los borran al terminar. No van a la capa de desarrollo, donde `keycloak-config-cli` duplicaría el TOTP al reaplicarla.
- **Antes de producción:** IN‑09 se cumple completa. Eso incluye las imágenes publicadas, el `compose` de `infra/` con Caddy y TLS, los usuarios de e2e de `infra/keycloak/` y el CI de e2e. Queda como T24 de la feature 001, junto a T19. **No se despliega a producción sin T19 ni T24.**
- **F0‑12:** en la Fase 0 se cumple con el e2e local de T23; la versión en CI llega con T24.

## Consecuencias

- **Positivas:**
  - cada feature se prueba en el navegador desde hoy, sin infraestructura nueva;
  - los criterios que solo se demuestran en el navegador pueden marcarse.
- **Negativas o costos:**
  - mientras tanto no se prueban las cookies con TLS, ni `SameSite` entre dos hosts, ni las imágenes reales;
  - las pruebas corren a mano. Si nadie las corre, un fallo puede pasar inadvertido; por eso el paso de la *skill* las exige al validar cada feature;
  - las capturas dependen del navegador y del sistema operativo de quien las genera, así que conviene generarlas siempre con el mismo navegador de Playwright.
- **Qué cambia en el repositorio:**
  - `docs/arquitectura/01` (IN‑09 y el mapa del repositorio), `00` (§6 y F0‑12), `03` (Compose del sistema) y `04` (§5, Q10 y el e2e en la tabla de pruebas);
  - `AGENTS.md` §4.2 (paso 7) y §4.6, y la *skill* `liv-sdd-feature`;
  - la tarea T23 de la feature 001, que se divide en el e2e local y la parte de antes de producción;
  - `ESTADO.md`.
