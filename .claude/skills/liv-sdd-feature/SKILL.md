---
name: liv-sdd-feature
description: Crea y conduce una feature del proyecto LIV con el flujo SDD (spec → plan → tareas → código con TDD). Úsala cuando el usuario pida una funcionalidad nueva o un cambio normal o crítico, pida "crear una feature", "escribir la spec", "hacer el plan" o "desglosar tareas", o cuando haya que avanzar o cerrar una feature en spec/features/. No la uses para cambios triviales (texto, formato, parche de dependencia).
---

# Feature SDD del proyecto LIV

Reemplaza a los comandos SDD de gentle‑ai. La metodología completa está en `spec/README.md` y `AGENTS.md` §4; esta *skill* es el procedimiento paso a paso.

## 0. Antes de empezar

1. Lee `ESTADO.md` y `spec/constitution/roadmap.md`. Si la Fase 0 no está cerrada, solo se aceptan features de la Fase 0 o de documentación.
2. Clasifica el cambio con `AGENTS.md` §4.3. Si es **trivial**, no uses esta *skill*: va directo a un PR.
3. Lee los documentos funcionales de `docs/funcional/` que toca la feature y anota las secciones y los códigos de regla (`R0xx`, `LQ-*`, `CJxx`, `CA-x.xx`…).

## 1. Crear la carpeta

1. Busca el siguiente número libre: lista `spec/features/` e ignora la plantilla `NNN-nombre-feature`.
2. Copia `spec/features/NNN-nombre-feature/` a `spec/features/<NNN>-<nombre-corto>/` (minúsculas con guiones, p. ej. `001-vigencias-fiscales`).
3. Reemplaza `NNN` y `<Nombre de la feature>` en los tres archivos.

## 2. Escribir `spec.md`

- Completa el encabezado: módulo, menú, tipo de cambio y fuentes (documento y sección).
- **Qué hace** y **Por qué**: desde el punto de vista del usuario, sin detalles técnicos.
- **Criterios de aceptación**: uno por regla o caso, en formato Dado / Cuando / Entonces, empezando por su código. Agrega los de segregación de funciones y aislamiento entre administraciones cuando apliquen.
- **Fuera de alcance** y **Preguntas abiertas**. Si un pendiente funcional (`PENDIENTE`, `POR_CONFIRMAR`) puede cambiar un criterio, **detente y pregunta**.
- Deja **Estado: propuesta** y pide la **aprobación H1** del responsable funcional. No sigas sin ella.

## 3. Diseño (solo si hay pantallas)

Cada pantalla nueva o modificada necesita su *frame* aprobado en la página `03 Pantallas` de Figma, o una declaración de reutilización de estructura (`AGENTS.md` §6.2). Usa la *skill* `liv-figma`.

## 4. Escribir `plan.md`

- **Enfoque** e **Implementación** paso a paso, indicando en cada paso si es de `backend/`, `frontend/` o de la raíz. Un PR toca un solo proyecto (`AGENTS.md` §4.6).
- **Datos, API y seguridad**: migraciones, cambios en el OpenAPI (el contrato va antes que el frontend), permisos y amenazas.
- **Diseño de interfaz**: enlaces a los *frames* y componentes de `liv-components`.
- **Decisiones** y **Riesgos**. Una decisión de arquitectura nueva se registra con la *skill* `liv-adr`.
- Ejecuta la *skill* `liv-revision-adversarial` sobre el plan y anota el resultado en la sección **Revisión**.
- Pide la **aprobación H2** (arquitecto; más el responsable funcional si cambia una regla o hay pantallas).

## 5. Escribir `tasks.md`

- Tareas pequeñas (un PR revisable, ≤ ~400 líneas), cada una con **Archivos** permitidos y **Prueba primero**.
- Si la feature toca backend y frontend, separa las tareas por proyecto: primero las del backend con el contrato.
- Conserva las tareas fijas de cierre de la plantilla.

## 6. Implementar (por tarea)

1. Rama `feat/<NNN-nombre>-<proyecto>`.
2. **Rojo:** escribe la prueba con el código de la regla en su nombre y etiqueta. Ejecútala y confirma que falla por el motivo correcto. Commit `test:`.
3. **Verde:** el código mínimo que la hace pasar. Commit `feat:` o `fix:`.
4. **Refactorizar** con las pruebas en verde. Commit `refactor:`.
5. Toca solo los archivos declarados en la tarea. Si necesitas otro, actualiza `tasks.md` y avisa.
6. Antes de cada commit, ejecuta la lente de legibilidad de `liv-revision-4r`.
7. Marca la tarea `[x]` en `tasks.md`.

## 7. Validar

- Ejecuta `liv-revision-adversarial` sobre lo implementado. Si el cambio es crítico o supera 400 líneas, ejecuta también las cuatro lentes de `liv-revision-4r`.
- Marca cada criterio de `spec.md` con `[x]` y el nombre de la prueba que lo demuestra. Sin prueba no se marca.
- Ejecuta las verificaciones locales y reporta con honestidad lo que falle.
- Abre el PR (uno por proyecto) citando la feature. La **aprobación H3** y el merge son siempre de un humano.

## 8. Cerrar

- Pasa la feature a "Hecho" en `spec/constitution/roadmap.md`.
- Actualiza `ESTADO.md` con la *skill* `liv-estado`.
- Si surgieron términos nuevos, agrégalos al glosario (`docs/arquitectura/01` §15).
