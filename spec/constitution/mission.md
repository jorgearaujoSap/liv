# Misión

_Define la razón de ser del proyecto. Es la referencia que decide si una feature "encaja" o no._

## Qué construimos

Un sistema para liquidar, recaudar y controlar el impuesto anual sobre vehículos automotores en Colombia (Ley 488 de 1998), que cualquier municipio, distrito o departamento adapta a sus reglas mediante una configuración por jurisdicción y vigencia, sin cambiar el código. Primer caso: municipio de Ipiales (Nariño).

1. **Tablas base y configuración** — cargue anual controlado de los avalúos del Ministerio de Transporte, tarifas, calendario, beneficios y la configuración de la jurisdicción (Doc. 01, Doc. 03).
2. **Homologación y liquidación** — cruce de cada vehículo con su avalúo oficial y cálculo del impuesto, individual, simulado o masivo (Doc. 02).
3. **Cuenta corriente y recaudo** — obligaciones, recibos, pagos, conciliación bancaria, imputación, saldos a favor y distribución del recaudo (Doc. 04).
4. **Cartera y cobro** — cartera histórica, intereses de mora, sanciones, prescripción, cortes y antigüedad (Doc. 05, borrador v0.1); cobro y medidas cautelares (futuro; ERD).

## Para quién

- **Funcionarios de la administración tributaria** (cargadores, aprobadores, liquidadores, recaudo, tesorería, jurídico): necesitan liquidar y recaudar con exactitud, rapidez y respaldo normativo.
- **Control interno y auditoría:** necesitan reconstruir cualquier liquidación o pago y saber quién hizo qué.
- **Administraciones territoriales** (municipios, distritos, departamentos): necesitan aplicar sus propias reglas y competencias.
- **Contribuyentes**, de forma indirecta: reciben liquidaciones correctas y recibos de pago. Un portal propio queda para más adelante.

## Principios

- **Ninguna regla tributaria en el código** — tarifas, rangos, fechas, descuentos y redondeo se leen de la configuración de jurisdicción publicada.
- **Exactitud y reproducibilidad** — aritmética decimal exacta, redondeo solo al final, y cada liquidación guarda la versión de reglas y catálogo con que se calculó.
- **Lo publicado no se modifica** — catálogos, configuraciones y movimientos contables se corrigen con nuevas versiones o reversos, nunca editando ni borrando.
- **Segregación y trazabilidad** — quien carga no aprueba, toda acción queda auditada y cada administración solo ve sus datos.
- **El soporte normativo manda** — ninguna regla se implementa sin fuente; lo que está `POR_CONFIRMAR` o `PENDIENTE` no se resuelve con supuestos.

## Qué NO es

- No toma decisiones jurídicas: calcula y propone, pero la exigibilidad, la prescripción y el título ejecutivo los decide un funcionario con soporte.
- No es un sistema contable ni financiero general de la entidad: lleva la cuenta corriente del impuesto vehicular.
- No reemplaza al RUNT ni al registro automotor: los consume como fuente.
- No es, por ahora, un portal del contribuyente ni una pasarela de pagos.
- No está atado a una sola jurisdicción: lo específico de Ipiales vive en su configuración.
