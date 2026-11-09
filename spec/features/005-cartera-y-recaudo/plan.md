# 005 · Cartera y recaudo: documentación funcional, configuración y esquema 1.1 — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** borrador
_(borrador → aprobado (H2))_

## Enfoque

<Estrategia general en pocas frases: qué aproximación se toma y por qué encaja con el stack y los principios del proyecto.>

## Implementación

_Pasos técnicos concretos, en orden. Indica los módulos/archivos que se tocan y la capa (dominio, aplicación, adaptador, frontend)._

1. <Paso — módulo/archivo afectado.>
2. <Paso — módulo/archivo afectado.>
3. <Paso — módulo/archivo afectado.>

## Datos, API y seguridad

_Borra las filas que no apliquen._

- **Migraciones:** <tablas, índices, restricciones y políticas RLS nuevas o cambiadas; *expand/contract* si aplica.>
- **Contrato OpenAPI:** <endpoints nuevos o cambiados; ¿algún cambio incompatible?>
- **Permisos:** <permisos que exige cada caso de uso y reglas de segregación de funciones.>
- **Amenazas:** <riesgos de seguridad de `docs/arquitectura/02` §2 que toca la feature y su control.>

## Diseño de interfaz

_Solo si la feature tiene pantallas. Borra esta sección si no aplica._

| Pantalla | Diseño en Figma (enlace al *frame* en `03 Pantallas`) o reutilización | Componentes de `liv-components` |
|---|---|---|
| <Número y nombre de menú> | <enlace> · o "Reutiliza la estructura de: <pantalla> (<enlace>); cambia: <contenido>" | <componentes> |

## Dependencias y recursos

_Qué necesita esta feature que no está en ella, y qué recursos compartidos usa. Con esto el orquestador ordena las tareas y decide qué corre en paralelo (`liv-orquestador`)._

- **Depende de:** <features, contratos o versiones de `liv-components` que deben estar integrados antes; o «nada».>
- **Recursos compartidos:** <contratos, versión de `liv-components`, migraciones o archivos de *build* de un proyecto, entorno local, Figma; y qué tareas los usan.>
- **Orden entre proyectos:** <p. ej. T1 (contrato, `backend/`) → T3 (`frontend/`); T2 (`ui/`) en paralelo con T1.>

## Decisiones

_Elecciones de diseño relevantes y su justificación. Alternativas descartadas y por qué. Una decisión de arquitectura se registra además como ADR._

- **<Decisión>** — <por qué; qué se descartó>.
- **<Decisión>** — <por qué; qué se descartó>.

## Riesgos

_Qué puede salir mal o requerir cuidado, y cómo se mitiga._

- **<Riesgo>** — <mitigación>.

## Revisión

- [ ] Revisión adversarial independiente del plan ejecutada; hallazgos resueltos o justificados.
- [ ] Aprobación H2: <quién, fecha>.
