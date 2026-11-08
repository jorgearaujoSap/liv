# ADR-0010 · Autenticación OIDC (Keycloak) con patrón BFF

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §5 y §7 (P2); `docs/arquitectura/02` §3

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Los *tokens* de acceso no deben llegar al navegador (doc. 02 §3), y la identidad es un Keycloak propio que se puede federar con un directorio de la entidad (doc. 02 §3; doc. 00 §7, P2).

## Opciones consideradas

No hay alternativas documentadas.

## Decisión

OIDC con *Authorization Code* y PKCE, ejecutado por un BFF: el navegador solo tiene una cookie de sesión (doc. 02 §3).

## Consecuencias

- **Positivas:** ningún *token* en el navegador.
- **Negativas o costos:** un componente más, el BFF, que guarda la sesión en el servidor.
- **Qué cambia en el repositorio:**
  - cookie `HttpOnly`, `Secure` y `SameSite=Strict`, con un *token* anti‑CSRF (doc. 02 §3);
  - segundo factor obligatorio para los roles sensibles (doc. 02 §3);
  - el doc. 02 §3 deja los tiempos de sesión (15 minutos de inactividad y 8 horas como máximo) «a confirmar». La feature 001 los aplicó;
  - el BFF usa Spring Cloud Gateway (doc. 00 §5). Se implementó en la feature 001, con las dependencias del ADR 0023.
