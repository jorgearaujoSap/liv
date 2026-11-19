## Qué

<!-- Qué cambia y por qué, en pocas líneas. -->

**Tamaño:** <!-- ajuste | rebanada | arranque o cierre de una feature (AGENTS.md §4.3) -->
**Issue:** <!-- «Parte de #n» en los PR intermedios; «Closes #n» solo en el último PR de la rebanada (GitHub solo cierra el issue con las palabras en inglés). En un ajuste, «no aplica». -->
**Zona roja, regla con código o documento funcional:** <!-- no | cuál (AGENTS.md §4.3 y §5.3) -->

## Definición de hecha (AGENTS.md §4.2 y §11)

<!-- Marca lo que aplica y escribe «no aplica» en lo que no. Un agente la llena antes de pedir la H3. -->

- [ ] Cada criterio, o el comportamiento que cambia, tiene su prueba en verde. Las del código que implementa una regla con código (§5.3) o un caso *golden* se escribieron antes, con su código de regla, y se vieron fallar.
- [ ] Las verificaciones locales y el CI están en verde, o se dice qué falló.
- [ ] Toca un solo proyecto (`AGENTS.md` §4.6) y solo lo de la rebanada o del ajuste; lo demás se propuso como *issue* (`liv-backlog`).
- [ ] Si toca una zona roja, una regla con código o un documento funcional, o es el arranque de una feature con zona roja: la revisión adversarial (dos revisores ciegos de nivel alto) no dejó críticos ni altos abiertos, y su resumen está abajo.
- [ ] Si hay pantalla: una captura o un video corto.
- [ ] El `CHANGELOG.md` del proyecto tiene su entrada; un ajuste de la raíz deja su línea en la bitácora de `ESTADO.md`.
- [ ] La documentación afectada está al día: documentos funcionales, de arquitectura, ADR, `README.md`, *skills* o `AGENTS.md`. Los términos nuevos del glosario, anotados en el *issue*.
- [ ] Ningún `TODO` sin su *issue*; cada atajo tiene su *issue* `deuda` aprobado por la persona, o está corregido.
- [ ] El tablero `liv-project` refleja el estado del *issue*.
- [ ] Último PR de la rebanada: con los demás PR de la rebanada integrados y este, funciona de punta a punta en local.
- [ ] Arranque o cierre de una feature: `ESTADO.md` y el *roadmap*. Al cerrar, además: las pruebas de navegador de los caminos principales en verde (ADR 0030), los criterios de `spec.md` marcados con su prueba, el glosario, la deuda revisada y la opción abierta en el menú.

## Revisión

<!-- Resumen de la revisión adversarial (hallazgos por severidad y qué se hizo), o «no aplica: no toca zona roja, regla con código ni documento funcional». -->
