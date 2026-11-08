# ADR-0020 · Alojamiento en AWS: ECS sobre Fargate, RDS for PostgreSQL, S3, ECR, Secrets Manager y KMS; sin servicios que impidan llevar la aplicación a otro proveedor

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2 (D14) y §7 (P1 y P6); `docs/arquitectura/03` §5

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El equipo decidió una nube pública, AWS (doc. 00 §7, P1). El sistema maneja datos personales (impulsor D14), y su disponibilidad depende del RPO y el RTO que defina la entidad (doc. 00 §7, P6).

## Opciones consideradas

No se nombran otros proveedores. Dentro de AWS, EKS solo se usaría si un ADR lo justifica (doc. 03 §5).

## Decisión

AWS con servicios que tienen un equivalente en otros proveedores: ECS sobre Fargate, RDS for PostgreSQL, S3, ECR, Secrets Manager y KMS. Ningún servicio que impida llevar la aplicación a otro proveedor (doc. 03 §5).

## Consecuencias

- **Positivas:** servicios administrados, sin quedar atados a AWS.
- **Negativas o costos:** no están documentados.
- **Qué cambia en el repositorio:**
  - la infraestructura como código va en `infra/`, con Terraform u OpenTofu; la herramienta exacta se fija en un ADR de la Fase 0 (doc. 03 §5);
  - cuentas separadas por entorno, con acceso por OIDC (doc. 03 §5);
  - **pendiente:** la región, por la Ley 1581 de datos personales (doc. 03 §5; doc. 00 §7, P1).
