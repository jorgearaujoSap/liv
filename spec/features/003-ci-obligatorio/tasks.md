# 003 · CI obligatorio en main — Tareas

_Checklist accionable derivada del `plan.md`. Tareas pequeñas y concretas; cada una cabe en un PR revisable (≤ ~400 líneas). Cada tarea indica los archivos que puede tocar y la prueba que se escribe primero (TDD: rojo → verde → refactorizar). Marca `[x]` al completarlas._

Rama de implementación: `feat/003-ci-obligatorio-github`. **T1 a T4 van en un solo PR de la raíz**: los *workflows* no se pueden probar por partes, y el PR que crea el *script* hace correr todo, porque toca `.github/` (plan, paso 3).

- [x] **T1** · Arnés de pruebas y *script* de decisión (plan, pasos 1 y 2).
  - **Archivos:** `.github/scripts/test/aplica.test.sh` y `.github/scripts/aplica.sh`, más `.github/.gitattributes` (final de línea LF de los *scripts*, para que no se rompan en Git Bash con `autocrlf`).
  - **Prueba primero:** el arnés, con todos los casos del paso 1, sobre un repositorio temporal con un *merge* real. Commit `test:` con el arnés en rojo (sin el *script*); después, el `feat:` con el *script*.
  - **Verificación local:** `bash .github/scripts/test/aplica.test.sh` en verde, en Git Bash y en Linux (CI).
- [x] **T2** · Trabajo `sistema · scripts de CI` en `sistema-pr`, que corre el arnés en todos los PR (plan, paso 1).
  - **Archivos:** `.github/workflows/sistema-pr.yml`.
- [x] **T3** · *Workflows* de cada proyecto (plan, pasos 3 a 5).
  - **Archivos:** `.github/workflows/backend-pr.yml`, `bff-pr.yml`, `frontend-pr.yml`, `ui-pr.yml` y `backend-contracts-pr.yml`.
  - **Qué cambia:**
    - sin `paths` en el disparador;
    - el trabajo `<proyecto> · cambios`, con el *script* de la base;
    - la verificación con la condición `always()` del paso 4;
    - el agregado `backend · contratos`;
    - un comentario que dice que los nombres de los trabajos son los de la protección.
  - **Prueba:** la ejecución del propio PR. Todo corre porque toca `.github/`, y `backend · contratos` queda en verde.
- [x] **T4** · Documentación (plan, paso 8).
  - **Archivos:** `docs/arquitectura/04_Calidad_Testing_y_CI.md` (§9 y su versión) y `ESTADO.md` (F0‑07 y bitácora).
- [ ] **T5** · Protección de `main` (plan, paso 6). Después de integrar el PR de T1 a T4, y **con la aprobación explícita del responsable** en la sesión (`AGENTS.md` §4.5).
  - **Qué se hace:** los seis *checks* nuevos al *ruleset* `main` (id 26341460), sin actores que se la salten y sin exigir la rama al día.
  - **Archivos:** `spec.md`, con el JSON resultante como prueba de 003-06.
- [ ] **T6** · Demostración (plan, paso 7). PR de prueba que se cierran sin integrar.
  - **Archivos:** solo los de cada PR de prueba, y `spec.md` con sus enlaces y su `mergeStateStatus`, como prueba de 003-01 a 003-05.
  - **003-07:** la salida de `git diff` sobre los `uses:`.
- [ ] Revisión adversarial independiente de lo implementado (dos revisores de nivel alto) y las cuatro lentes de `liv-revision-4r`: es un cambio crítico.
- [ ] Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra.
- [ ] Compuertas de CI en verde y PR integrado por un humano (H3).
- [ ] Cerrar:
  - mover F0‑07 en `ESTADO.md` (condición 2 del ADR 0031 cumplida);
  - la feature a «Hecho» en `../../constitution/roadmap.md`.

## Mantenimiento (checklist recurrente)

- [ ] Una carpeta o un archivo nuevo en la raíz hace correr todo hasta que se declare neutro en `.github/scripts/aplica.sh`, con su caso en el arnés.
- [ ] Si cambia el nombre de un trabajo de verificación, cambiar también el *ruleset* `main` en el mismo momento; si no, el *check* obligatorio queda esperando.
