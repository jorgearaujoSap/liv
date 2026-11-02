# ADR-0024 · Identidad como proyecto `backend/identity/` y excepción del tema de Keycloak

- **Estado:** Aceptado. Modifica en parte el ADR 0018, solo para el tema de Keycloak.
- **Fecha:** 2026-11-01
- **Decisores:** arquitecto y responsable funcional (aprobación H2 de la feature 001; la excepción del tema la decidió el responsable funcional ese mismo día)
- **Documentos relacionados:** `spec/features/001-inicio-de-sesion/plan.md` (pasos 3 y 14, Decisiones); ADR 0010, 0013, 0018, 0019; `docs/arquitectura/01` §11.1 y §14.1; `docs/arquitectura/02` §3

## Contexto

Keycloak (ADR 0010) necesita tres cosas que deben versionarse y revisarse como código:

- la configuración del *realm*: roles, clientes, segundo factor, bloqueo, tiempos y eventos;
- el tema de las páginas de acceso, con su diseño aprobado en Figma;
- la imagen que los une.

Las páginas de acceso las sirve Keycloak con plantillas FreeMarker. No pueden usar los componentes Angular de `liv-components`, y eso choca con el ADR 0018 ("las pantallas solo componen componentes de la librería").

## Opciones consideradas

1. **Proyecto `backend/identity/`** con el *realm* como código (una base para todos los entornos y una capa solo de desarrollo), el tema FreeMarker compilado con los *tokens* del paquete npm `liv-components` y la imagen publicada.
2. **Tema y *realm* en `infra/`.** Va contra IN‑09: el *compose* de desarrollo del backend necesita la imagen, e `infra/` solo usa imágenes publicadas.
3. **Keycloakify (React).** Permite componentes, pero agrega un segundo *stack* de interfaz y una dependencia grande.
4. **Inicio de sesión propio en Angular.** Va contra el ADR 0010 y contra la independencia del proveedor (D-001-6).

## Decisión

Opción 1.

- **Configuración base del *realm*.** Vive en `backend/identity/config/base/` y se aplica en todos los entornos con `keycloak-config-cli`, con variables por entorno.
- **Capa de desarrollo.** `config/dev/` tiene los usuarios y secretos de prueba, y nunca se aplica fuera de desarrollo; CI lo verifica.
- **Imágenes.** Se publican la de Keycloak con el tema y la de configuración (`liv-identity-config`), que `infra/` también usa.
- **Excepción al ADR 0018, solo para el tema de Keycloak:**
  - el tema reproduce los componentes aprobados (Button, TextField, Alert, CodeInput…) con el CSS compilado desde los *tokens* del paquete `liv-components`, con versión exacta y sin leer `../ui`;
  - `Keycloak/BrandPanel` y `Keycloak/Step` existen solo en el tema y así se documentan en `02 Componentes`;
  - cada versión de la librería exige una prueba visual del tema contra los *frames* aprobados.

## Consecuencias

- **Positivas:** la seguridad del *realm* se puede reproducir, revisar en PR y probar. El acceso tiene el mismo aspecto que la aplicación sin otro *stack* de interfaz.
- **Negativas o costos:** el tema duplica la apariencia de unos pocos componentes. La deriva se controla con la prueba visual. Algunos comportamientos de Keycloak (el aviso de bloqueo, el motivo del cierre de sesión) dependen de un *spike*.
- **Qué cambia en el repositorio:**
  - estructura del backend (`backend/identity/`, doc. 01 §14);
  - rutas críticas de `AGENTS.md` §4.3 (`backend/identity/**`);
  - descripción de la excepción en doc. 01 §11.1.
