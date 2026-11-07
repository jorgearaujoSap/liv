---
name: liv-prueba-e2e
description: Escribe o cambia una prueba de navegador de LIV (Playwright, en e2e/) con las convenciones del proyecto: un criterio de la spec por prueba con su código, sesión con los usuarios ficticios por rol, localizadores por rol y texto visible, axe y captura de referencia. Úsala en cada feature con interfaz, antes de implementar sus pantallas, y cuando una prueba e2e falle o deba cambiar.
---

# Nueva prueba e2e

Las pruebas de navegador recorren LIV como una persona, contra el sistema levantado en local y solo por HTTP (ADR 0030). Cada feature con interfaz agrega aquí sus pruebas, **primero en rojo y en un PR propio de `e2e/`** (`liv-sdd-feature`, paso 7). Esta *skill* dice **cómo** se escriben. Lo demás está en `e2e/README.md`.

Para la API de Playwright consulta la documentación oficial de la versión de `package.json` (1.64.1). No escribas de memoria.

## Ejemplos que se siguen

| Archivo | Qué muestra |
|---|---|
| `pruebas/estructura.spec.ts` | Una pantalla después de iniciar sesión: contenido por rol, axe y captura |
| `pruebas/acceso.spec.ts` | Inicio de sesión, mensajes de error y segundo factor |
| `support/acceso.ts` | `entrarComo`, `ingresar`, `escribirCodigo` e `infraccionesDeAccesibilidad` |
| `support/entorno.ts` | Direcciones y usuarios propios de las pruebas (`USUARIOS_E2E`) |

## Pasos

1. **Un criterio, una prueba.** Toma los criterios de `spec.md` que se ven en el navegador. Cada prueba lleva el código del criterio al inicio de su nombre, en español: `test('001-02 · Inicio muestra en la cabecera el nombre, la administración y el rol', …)`. Agrúpalas por pantalla con `test.describe` en `pruebas/<pantalla>.spec.ts`.
2. **Sesión.**
   - Usa los usuarios ficticios de `backend/dev/keycloak/liv-realm-dev.yaml`, uno por rol. Los que no tienen segundo factor entran con `entrarComo(page, '<usuario>')`. Los que sí lo tienen, con `ingresar` y `escribirCodigo`, como `entrarComoAdministrador` en `pruebas/estructura.spec.ts`.
   - Si un caso necesita un usuario con un estado especial (bloqueado, con segundo factor), créalo en `crearUsuarios` (`support/keycloak.ts`) y agrega su nombre a `USUARIOS_E2E` (`support/entorno.ts`). Así `preparar.ts` lo crea y `limpiar.ts` lo borra, como `e2e.totp`.
   - Nunca uses datos reales ni escribas contraseñas o claves nuevas en las pruebas.
3. **Localizadores.**
   - Primero, lo que ve una persona: `page.getByRole('heading', { name: '…' })`, `getByRole('button', …)` y `getByText`.
   - Si no hay un rol ni un texto únicos, usa la etiqueta del componente de `liv-components` (`page.locator('liv-app-header')`).
   - En las páginas de acceso, que son del tema de Keycloak y no de LIV, usa los `id` del tema (`#username`, `#kc-login`), mejor a través de las funciones de `support/acceso.ts`.
   - Nunca uses clases internas de un componente ni posiciones (`nth`), salvo que el criterio trate del diseño de ese componente, como las tarjetas iguales de 001-02 (`.module-card__link` en `pruebas/estructura.spec.ts`).
4. **Lo que se comprueba en cada pantalla.**
   - El contenido del criterio, con `expect(…).toBeVisible()`, `toContainText()` y `toHaveURL()`.
   - Accesibilidad: `expect(await infraccionesDeAccesibilidad(page)).toEqual([])`.
   - Diseño: `await expect(page).toHaveScreenshot('<pantalla>-<caso>.png', { fullPage: true })`. La primera captura de cada pantalla la revisas contra el *frame* de Figma y la aprueba el responsable. Nunca la regeneres para que una prueba pase. El nombre del archivo lleva el navegador y el sistema operativo (`-chromium-win32.png`): en otro sistema, la primera corrida genera capturas nuevas, que también se revisan.
5. **Esperas.** Usa las aserciones de Playwright, que esperan solas. Una pausa fija (`waitForTimeout`) solo con un comentario que diga su motivo, como la espera entre intentos de `pruebas/acceso.spec.ts`.
6. **Rojo.** Levanta el sistema desde la raíz (`sh tools/dev/iniciar.sh`) y corre la prueba: `npx playwright test pruebas/<pantalla>.spec.ts`. Antes de que exista la pantalla debe fallar por el criterio, no por la sesión ni por el entorno.

## Verificar

- Desde `e2e/`: `npm test`, y después `npm run informe` para ver lo que falló, con capturas, videos y trazas.
- Las capturas nuevas o cambiadas van en `pruebas/*.spec.ts-snapshots/`, en el mismo PR. Solo se regeneran con `npm run test:actualizar-capturas`, después de un cambio visual que aprobó el responsable.
- Al terminar: `sh tools/dev/detener.sh`.

## Antes del *commit*

- [ ] Cada prueba nombra su criterio y prueba solo ese criterio.
- [ ] Hay una prueba por rol cuando el criterio cambia según el rol, y una del caso sin permiso cuando aplica.
- [ ] Los localizadores son por rol, texto o etiqueta del componente. Solo hay clases internas cuando el criterio trata del diseño de ese componente, y ninguna pausa fija sin su motivo escrito.
- [ ] Cada pantalla nueva tiene su verificación con axe y su captura revisada contra Figma.
- [ ] Solo se usan usuarios ficticios. Los que crea la prueba están en `USUARIOS_E2E`, para que `limpiar.ts` los borre.
- [ ] `npm test` pasó contra el sistema levantado, o se reporta qué falló.
