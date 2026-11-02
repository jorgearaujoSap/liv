# Roadmap

_Orden y estado de las features. Es la vista de "qué hay hecho, qué toca ahora y qué viene". Cada entrada apunta a su carpeta en `features/`._

> Las fases y sus criterios de salida están en `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` §8. El estado general del proyecto (entregables de la Fase 0, decisiones abiertas, Figma, bitácora) está en `ESTADO.md`. Al mover una feature de sección, se actualizan este archivo y `ESTADO.md` en el mismo cambio.

## Hecho ✅

_Features completadas, en orden de implementación._

_(ninguna todavía)_

## Siguiente 🔜

_Lo próximo a abordar. Idealmente una sola feature "en curso" a la vez._

- 🔄 **[001 · Inicio de sesión](../features/001-inicio-de-sesion/spec.md)** — en curso: spec (H1, con enmienda 1), diseño y plan (H2) aprobados; `tasks.md` con 23 tareas, lista para implementar. Feature de plataforma (no de negocio) y guía de referencia del flujo SDD; incluye el entregable F0‑05b (diseños de inicio de sesión, *layout* y menú).
- **Fase 0 — Fundaciones** — no son features de negocio: se siguen como entregables F0‑01 a F0‑12 en `ESTADO.md`. Ninguna feature de negocio empieza antes de cerrar la Fase 0.

## Backlog / ideas 💡

_Sin comprometer ni ordenar del todo. El número `NNN` se asigna al crear la carpeta de la feature. Agrupadas por fase de `docs/arquitectura/00` §8._

**Fase 1 — Tablas base y configuración**
- **Vigencias fiscales** (1.4) — apertura de vigencia y habilitación de la liquidación con doble firma.
- **Normas y soportes** (1.3) — registro de normas con su PDF.
- **Cargue de tablas de avalúos** (1.5.1–1.5.4) — importación XLSX del Ministerio con validación, aprobación y publicación.
- **Tarifas, calendario, beneficios y sanciones** (1.5.5–1.5.8).
- **Configuración de jurisdicción** (1.5.9) — carga, validación CJ01–CJ14 y publicación del YAML.
- **Consultas y exportación de tablas base** (1.6.1–1.6.7), incluido el reporte SIBGA.

**Fase 2 — Homologación y liquidación individual**
- **Homologación vehículo ↔ avalúo** (2.6) — cascada de 8 niveles.
- **Motor de liquidación, liquidación individual y simulador** (3.1, 3.3).

**Fase 3 — Liquidación masiva**
- **Corridas masivas y excepciones** (3.2, 3.6).

**Fase 4 — Cuenta corriente y recaudo**
- **Obligaciones** (4.1), **recibos y pagos** (4.2), **imputación** (4.3), **saldos a favor y reversiones** (4.4), **distribución del recaudo** (4.5), **control del 20 %** (4.6), **estado de cuenta y paz y salvo** (4.7).

**Fases 5 y 6** — cartera, cobro y medidas cautelares: pendientes de su documento funcional.

**Después del piloto** (decisiones P8 y P9)
- **Firma digital** de liquidaciones oficiales y paz y salvo con un proveedor certificado.
- **Portal del contribuyente** — consulta, recibo y pago por PSE.

> Cada feature nueva se crea como `features/NNN-nombre-feature/` con `spec.md`, `plan.md` y `tasks.md` antes de tocar código.
