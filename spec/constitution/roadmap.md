# Roadmap

_Orden y estado de las features. Es la vista de "qué hay hecho, qué toca ahora y qué viene". Cada entrada apunta a su carpeta en `features/`._

> Las fases y sus criterios de salida están en `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` §8. El estado general del proyecto (entregables de la Fase 0, decisiones abiertas, Figma, bitácora) está en `ESTADO.md`. Al mover una feature de sección, se actualizan este archivo y `ESTADO.md` en el mismo cambio. Bajo un orquestador, lo hace él en su PR (ADR 0033). La feature en curso es la que figura en el tablero «Trabajo en curso» de `ESTADO.md`.

## Hecho ✅

_Features completadas, en orden de implementación._

- ✅ **[001 · Inicio de sesión](../features/001-inicio-de-sesion/spec.md)** — hecha el 2026-11-06. Inicio de sesión con Keycloak por el BFF (sin *tokens* en el navegador), segundo factor por rol, sesión con inactividad y duración máxima, menú por permisos de tres niveles, *layout* y pantallas de error. Endurecida (T19) y con pruebas de navegador (T23). Los 24 criterios de la spec están probados. Quedan para T24, antes de producción: imágenes, TLS, el e2e en CI y la CSP con *nonce* del frontend. Feature de plataforma y guía de referencia del flujo SDD; incluye el entregable F0‑05b.
- ✅ **[003 · CI obligatorio en main](../features/003-ci-obligatorio/spec.md)** — hecha el 2026-11-07. Los *workflows* de PR corren siempre, cada verificación se omite solo si su proyecto no cambió y la protección de `main` exige la verificación de cada proyecto y los contratos (condición 2 del cierre de la Fase 0, ADR 0031).
- ✅ **[004 · Esquema de la configuración de jurisdicción](../features/004-esquema-configuracion-jurisdiccion/spec.md)** — hecha el 2026-11-08. JSON Schema 1.0 de la configuración de jurisdicción, del que el backend es dueño, con su carga segura del YAML y sus pruebas; inmutable en cada PR y publicado como *release* al integrarse (entregable F0‑10, ADR 0032). El validador de los anexos en la raíz y su CI pasaron a la feature 1.5.9.

- ✅ **[005 · Cartera y recaudo](../features/005-cartera-y-recaudo/spec.md)** — hecha el 2026-11-11. Documentación funcional, configuración y contrato, sin código de negocio:
  - Doc. 05 v0.1 (Módulo 5: intereses de mora, sanciones, prescripción, cartera histórica y cortes);
  - Doc. 04 v0.2 (bancos, Asobancaria 2001 y CSV canónico, imputación a la vigencia más antigua, reporte del 20 %), sin pasarela de pagos, que queda para después del piloto;
  - Doc. 03 v0.5 con el validador CJ01–CJ16;
  - ERD 2.2 con 79 tablas;
  - anexos YAML 1.1;
  - esquema de configuración 1.1, publicado en la *release* `jsonschema-config-jurisdiccion-v1.1`;
  - ADR 0034;
  - `AGENTS.md` al día.

  Fue la primera feature con el orquestador.

## Siguiente 🔜

_Lo próximo a abordar. Idealmente una sola feature "en curso" a la vez._

- ⏳ **T24 de la feature 001, antes de producción** — imágenes publicadas, `infra/` con TLS, el e2e en CI (F0‑12 completo), la CSP con *nonce* del frontend y las variables `LIV_BFF_TRUSTED_PROXIES` y `LIV_BFF_REPLICAS` del despliegue. No se despliega a producción sin ella (ADR 0030).
- **Fase 0 — Fundaciones** — no son features de negocio: se siguen como entregables F0‑01 a F0‑12 en `ESTADO.md`. Ninguna feature de negocio empieza antes de cerrar la Fase 0.

## Backlog / ideas 💡

_Sin comprometer ni ordenar del todo. El número `NNN` se asigna al crear la carpeta de la feature. Agrupadas por fase de `docs/arquitectura/00` §8._

**Fase 1 — Tablas base y configuración**
- **Vigencias fiscales** (1.4) — apertura de vigencia y habilitación de la liquidación con doble firma.
- **Normas y soportes** (1.3) — registro de normas con su PDF.
- **Cargue de tablas de avalúos** (1.5.1–1.5.4) — importación XLSX del Ministerio con validación, aprobación y publicación.
- **Tarifas, calendario, beneficios y sanciones** (1.5.5–1.5.8).
- **Configuración de jurisdicción** (1.5.9) — carga, validación CJ01–CJ16 y publicación del YAML. Al publicar, guarda la versión del validador y la del esquema (decisión 6 de la H3 de R3a, feature 005). Incluye el validador de los anexos en la raíz y su CI, que la feature 004 dejó para aquí (pasos 4 y 5 de su plan, criterio 004‑13).
- **Consultas y exportación de tablas base** (1.6.1–1.6.7), incluido el reporte SIBGA.

**Fase 2 — Homologación y liquidación individual**
- **Homologación vehículo ↔ avalúo** (2.6) — cascada de 8 niveles.
- **Motor de liquidación, liquidación individual y simulador** (3.1, 3.3).

**Fase 3 — Liquidación masiva**
- **Corridas masivas y excepciones** (3.2, 3.6).

**Fase 4 — Cuenta corriente y recaudo**
- **Obligaciones** (4.1), **recibos y pagos** (4.2), **imputación** (4.3), **saldos a favor y reversiones** (4.4), **distribución del recaudo** (4.5), **control del 20 %** (4.6), **estado de cuenta y paz y salvo** (4.7).

**Fase 5 — Cartera** (Doc. 05; resuelve antes los pendientes del Doc. 05 §11 que le toquen)
- **Tasas de mora** (5.2.1) — carga mensual de la certificación de la Superfinanciera, con soporte PDF y doble firma.
- **Intereses de mora y simulador** (5.2.2–5.2.4) — cálculo por tramos y causación incremental. Los ejemplos del Doc. 05 §4.3 pasan a *golden* cuando se resuelvan los pendientes 2, 10, 11 y 12.
- **Apertura de cartera histórica** (5.1) — lotes con validación CH‑01 a CH‑10, conciliación con el sistema anterior y aplicación.
- **Sanciones** (5.3) — propuestas y registro del acto en firme.
- **Prescripción** (5.4) — eventos de interrupción y suspensión, semáforo, expediente y acto.
- **Cortes de cartera y antigüedad** (5.5) y **consultas** (5.6).

**Fase 6 — Cobro y medidas cautelares**: pendientes de su documento funcional (Módulos 6 y 7).

**Después del piloto** (decisiones P8 y P9)
- **Firma digital** de liquidaciones oficiales y paz y salvo con un proveedor certificado.
- **Portal del contribuyente** — consulta, recibo y pago por PSE.

> Cada feature nueva se crea como `features/NNN-nombre-feature/` con `spec.md`, `plan.md` y `tasks.md` antes de tocar código.
