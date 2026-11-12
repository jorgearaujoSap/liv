# 006 · Pruebas *golden* en rojo

**Estado:** hecha — 2026-11-12, al integrar el PR #174 y este cierre. Los criterios de comportamiento se demuestran con pruebas *golden* escritas en rojo, como pide F0‑11: se ponen en verde con la Fase 2 (*issue* #171) y la Fase 4 (*issue* #173). Antes: aprobada (H1) — 2026-11-11, por la persona que dirige el trabajo (`AGENTS.md` §4.3), con las nueve recomendaciones de la spec (véase «Decisiones de la H1»). **H2 aprobada** el 2026-11-11 con las cuatro recomendaciones del plan: L14 y L15, el método del redondeo «al peso» como pendiente del Doc. 04, la redacción de 006‑01 y 006‑07, y los dos altos de la ronda 2 a la revisión del código
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `tax` y `ledger` (solo pruebas del *backend*; sin código de negocio) · **Menú:** ninguno (prepara los Módulos 3 y 4) · **Tipo de cambio:** crítico: pruebas en rutas críticas (`backend/**/liv/tax/**`, `backend/**/liv/ledger/**`) y pruebas desactivadas con `@Disabled` y su *issue*, aprobado en la H1 (decisión 1; `AGENTS.md` §4.3 y §4.5). Un PR de documento aparte registra un pendiente en el Doc. 04 (decisión 6)
**Fuentes:** Doc. 02 v0.2 §B.6 a §B.9 y §11 (CA‑L01 a CA‑L04); Doc. 03 v0.5 §9.1, §9.3, §9.6 y §9.7; Doc. 04 v0.2 §3.1, §5.1, §5.2, §7 y §13 (CA‑4.01 a CA‑4.09; la v0.3 de D1 solo agrega los pendientes 8 y 9 de §14); `docs/funcional/anexos/config_jurisdiccion_52356_2026.yaml` (esquema 1.1); `docs/arquitectura/00` §8.1 (F0‑11); `docs/arquitectura/04` §4.3, §5, §5.1 y §6 (Q02); `AGENTS.md` §4.5, §4.6, §5.1 y §5.3; `backend/AGENTS.md`; ADR 0016 y 0031

## Qué hace

Los documentos funcionales traen ejemplos con números: cuánto impuesto da un vehículo con tal base, cuánto se paga con descuento, cómo se reparte un pago entre impuesto e intereses. Hoy esos números solo están en el texto: nada comprueba que el sistema, cuando exista, dé lo mismo.

Con esta feature:
- cada ejemplo numérico de los Docs. 02, 03 y 04 que entra en el alcance (véase «Fuera de alcance») queda **transcrito a un archivo de datos** del *backend* (un *fixture*): la entrada, la configuración que usa, el resultado esperado al peso, el código de la regla y de dónde salió (documento, versión, sección y fila);
- cada caso es una **prueba *golden***: una prueba parametrizada que le pasa la entrada al motor de liquidación o a la cuenta corriente y compara lo que devuelven con lo que dice el documento;
- como el motor (Fase 2) y la cuenta corriente (Fase 4) todavía no existen, las pruebas **fallan** («en rojo») por esa razón y solo por esa. El día que el motor exista, se ponen en verde sin reescribirlas;
- el CI del *backend* sigue en verde y obligatorio: las pruebas *golden* llevan `@Disabled` con su *issue* (decisión 1) y se ejecutan en rojo con un comando documentado;
- una prueba en verde comprueba que los *fixtures* están completos y bien formados, para que no se dañen mientras esperan al motor.

La feature **no** implementa el motor ni la cuenta corriente, ni decide su diseño: fija **qué** deben dar, no **cómo**.

### Casos *golden*

Valores tal como están en los documentos, recalculados aparte con aritmética exacta (Python `Decimal`): coinciden todos al peso, y el bruto al centavo (decisión 7). Una celda vacía es un valor que el documento no da: la prueba no lo compara y la feature no lo inventa. «—» es un valor que el documento dice que no aplica.

**Liquidación (módulo `tax`)**

| Caso | Fuente | Entrada | Tarifa | Meses | Bruto | Neto redondeado | Descuento efectivo | A pagar con descuento | Fecha límite¹ |
|---|---|---|---:|---:|---:|---:|---:|---:|---|
| L1 | Doc. 02 §B.9, fila 1 | Automóvil particular usado, base $45.000.000 | 1,5 % | 12 | $675.000 | | | | |
| L2 | Doc. 02 §B.9, fila 2 | Automóvil particular usado, base $80.000.000 | 2,5 % | 12 | $2.000.000 | | | | |
| L3 | Doc. 02 §B.9, fila 3; CA‑L04 | Camioneta particular blindada, tabla $120.000.000 → base $132.000.000 | 3,5 % | 12 | $4.620.000 | | | | |
| L4 | Doc. 02 §B.9, fila 4; CA‑L03 | Nuevo, factura sin IVA $100.000.000, causación 15‑ago‑2026 | 2,5 % | 5 | $1.041.666,67 | | | | |
| L5 | Doc. 02 §B.9, fila 5; CA‑L01 | Base exactamente $57.349.000 | 1,5 % | 12 | $860.235 | | | | |
| L6 | CA‑L02 | Base $57.349.001 | 2,5 % | | | | | | |
| L7 | Doc. 03 §9.3, fila 1 | Automóvil, tabla 21.600 (miles) | 1,5 % | 12 | $324.000,00 | $324.000 | $32.000 | $292.000 | 30‑abr‑2026 |
| L8 | Doc. 03 §9.3, fila 2 | Camioneta, tabla 80.460 | 2,5 % | 12 | $2.011.500,00 | $2.012.000 | $201.000 | $1.811.000 | 30‑abr‑2026 |
| L9 | Doc. 03 §9.3, fila 3; CA‑L04 | Camioneta blindada, tabla 120.000 (+10 %) | 3,5 % | 12 | $4.620.000,00 | $4.620.000 | $462.000 | $4.158.000 | 30‑abr‑2026 |
| L10 | Doc. 03 §9.3, fila 4 | Internado temporalmente, tabla 45.640 (50 %) → base $22.820.000 | 1,5 % | 12 | $342.300,00 | $342.000 | $34.000 | $308.000 | 30‑abr‑2026 |
| L11 | Doc. 03 §9.3, fila 5 | Moto de más de 125 cc, tabla 6.590 | 1,5 % | 12 | $98.850,00 | $99.000 | $10.000 | $89.000 | 30‑abr‑2026 |
| L12 | Doc. 03 §9.3, fila 6 | Eléctrico, tabla 83.330 | 1,0 % | 12 | $833.300,00 | $833.000 | $83.000 | $750.000 | 30‑abr‑2026 |
| L13 | Doc. 03 §9.3, fila 7; CA‑L03 | Nuevo, factura sin IVA $100.000.000, causación 15‑ago‑2026 | 2,5 % | 5 | $1.041.666,67 | $1.042.000 | — (sin descuento, §9.7) | — | 31‑dic‑2026 |

¹ En L7 a L12, la fecha hasta la que se paga con descuento (`fecha_limite_descuento`); en L13, el plazo sin intereses del vehículo nuevo (Doc. 03 §9.7), porque no tiene descuento.

**Fechas del calendario de vehículos nuevos** (módulo `tax`; Doc. 03 §9.7, con los valores iniciales de Ipiales). La fila del 15‑ago es la de L13 y no se repite. Agregadas en la H2.

| Caso | Fuente | Entrada | Descuento hasta | Sin intereses hasta | Mora desde |
|---|---|---|---|---|---|
| L14 | Doc. 03 §9.7, fila 1 | Vehículo nuevo, causación 10‑mar‑2026 | 30‑abr‑2026 | 30‑abr‑2026 | 1‑may‑2026 |
| L15 | Doc. 03 §9.7, fila 3 | Vehículo nuevo, causación 20‑dic‑2026 | — (no aplica) | 31‑dic‑2026 | 1‑ene‑2027 |

**Cuenta corriente (módulo `ledger`)**

| Caso | Fuente | Entrada | Resultado esperado |
|---|---|---|---|
| C1 | Doc. 04 §3.1; CA‑4.01 | Emisión de la liquidación del internado (L10): neto $342.000 | Obligación con `original_principal` $342.000 (sin restar el descuento, LQ‑C04), `due_date` 30‑abr‑2026, modalidad `UEDF_INTERNACION` y movimiento `ASSESSMENT` TAX +342.000 |
| C2 | Doc. 04 §5.2, ejemplo 1; CA‑4.02 | Neto $342.000; pago de $308.000 el 20‑abr‑2026 | `PAYMENT` TAX −308.000 y `EARLY_PAYMENT_DISCOUNT` TAX −34.000; saldo 0; estado `PAGADA` |
| C3 | CA‑4.03 | Neto $342.000; pago de $200.000 el 20‑abr‑2026 (parcial) | Sin descuento (`descuento_pago_parcial: NO_APLICA`); saldo $142.000 |
| C4 | Doc. 04 §5.2, ejemplo 2; CA‑4.04 | Deuda dada: impuesto $342.000 + intereses $15.000; pago de $200.000 | Imputado: impuesto $191.597 e intereses $8.403; saldo: impuesto $150.403, intereses $6.597, total $157.000 |
| C5 | Doc. 04 §5.2, ejemplo 2 (variante) | Deuda dada: impuesto $342.000 + intereses $15.000 + sanción $30.000; pago de $200.000 | Imputado: impuesto $176.744, intereses $7.752 y sanción $15.504 |
| C6 | CA‑4.08 | El caso C2 y luego el reverso bancario del pago | `REVERSAL` del pago y del descuento; saldo $342.000 |
| C7 | Doc. 04 §7, fila 1 | Con B, pago de $2.012.000 (sin descuento), dirección declarada en Ipiales | Nariño 80 % $1.609.600; municipio 20 % $402.400 retenidos |
| C8 | Doc. 04 §7, fila 2 | Con B, pago de $1.811.000 (con descuento), dirección en Ipiales | Nariño $1.448.800; municipio $362.200 retenidos |
| C9 | Doc. 04 §7, fila 3; CA‑4.09 | Con B, pago de $342.000, dirección en Pasto | Nariño $273.600; $68.400 girados a Pasto |

CA‑4.07 (pago de $350.000 sobre un recibo de $342.000) queda fuera: no da la fecha del pago, y con las fechas de Ipiales no da $8.000 (decisión 6).

## Por qué

- Es el entregable **F0‑11**, la última condición que falta para cerrar la Fase 0 (ADR 0031). Sin ella no empieza ninguna feature de negocio.
- Los ejemplos de los documentos son la especificación ejecutable del motor (`docs/arquitectura/04` §5.1). Escribirlos antes que el motor obliga a que el motor se construya contra ellos (TDD), y no al revés.
- Transcribirlos ahora destapa los huecos de los ejemplos (valores ilustrativos, fechas que faltan, precisión) mientras arreglarlos solo cuesta corregir un documento.

## Criterios de aceptación

**Liquidación**

- [x] **006‑01** — Dados los casos L1 a L15 de «Casos *golden*», cuando se transcriben, entonces cada uno tiene su *fixture* con la entrada, la referencia a la copia de la configuración de Ipiales que usa, con su SHA‑256 (decisión 4), el resultado esperado de cada columna con valor y su fuente (documento, versión, sección y fila). Ningún *fixture* trae un valor **esperado** que el documento no dé, ni otros casos que los de la tabla. Una **entrada** que el documento no da pero el caso necesita (p. ej. la categoría de L5 y L6) va marcada como inferida, con su motivo (ajuste de la H2, revisión adversarial). _Prueba:_ `AssessmentGoldenFixturesTest` (en verde): «Los casos de golden/tax son exactamente L1 a L15», «Cada caso compara exactamente las columnas con valor de la tabla de la spec», «Las entradas inferidas están declaradas con su motivo y su supuesto» y «Cada caso usa la copia de Ipiales por su ruta y su SHA-256»; *fixtures* de `backend/src/test/resources/golden/tax/` (PR #172)
- [x] **CA‑L01 · CA‑L02** — Dadas las bases $57.349.000 (L5) y $57.349.001 (L6), cuando se liquidan, entonces la tarifa es 1,5 % (borde superior inclusivo) y 2,5 %, y el bruto de L5 es $860.235. _Prueba:_ `AssessmentGoldenTest` («La base en el borde de un tramo paga la tarifa de ese tramo (CA-L01)» con L5 y «Un peso sobre el borde de un tramo paga la tarifa del tramo siguiente (CA-L02)» con L6), en rojo hasta la Fase 2, *issue* #171
- [x] **CA‑L03** — Dado el vehículo nuevo con causación el 15‑ago‑2026 (L4 y L13), cuando se liquida, entonces tiene 5 meses gravables y bruto $1.041.666,67 (comparado al centavo, decisión 7), y con la configuración de Ipiales, neto $1.042.000, sin descuento y fecha límite 31‑dic‑2026. _Prueba:_ `AssessmentGoldenTest` («Un vehículo nuevo paga los meses que quedan de la vigencia (CA-L03)», L4 y L13), en rojo hasta la Fase 2, *issue* #171
- [x] **CA‑L04** — Dada la camioneta blindada con tabla $120.000.000 (L3 y L9), cuando se liquida, entonces la base ajustada es $132.000.000 y la tarifa es la del tramo de esa base, 3,5 %, con bruto $4.620.000. _Prueba:_ `AssessmentGoldenTest` («El ajuste de la base puede cambiar el tramo y la tarifa (CA-L04)», L3 y L9), en rojo hasta la Fase 2, *issue* #171
- [x] **006‑02** — Dados los automóviles usados de L1 y L2, cuando se liquidan, entonces la tarifa y el bruto son los de la tabla ($675.000 y $2.000.000). _Prueba:_ `AssessmentGoldenTest` («Los automóviles usados de la tabla dan su tarifa y su bruto (006-02)», L1 y L2), en rojo hasta la Fase 2, *issue* #171
- [x] **LQ‑C01 · LQ‑C02 · LQ‑C04** — Dados los casos L7 a L13 con la configuración de Ipiales, cuando se liquidan, entonces el neto redondeado, el descuento efectivo, el valor a pagar con descuento y la fecha límite son los de la tabla: redondeo al $1.000 más cercano solo al final, un $500 exacto sube (L8), el descuento es la diferencia entre el neto y el valor a pagar redondeado (Doc. 03 §9.3, nota 1) y no reduce el neto. _Prueba:_ `AssessmentGoldenTest` («El redondeo se aplica solo al final del cálculo (LQ-C01)», «El neto y el valor con descuento se redondean como dice la configuración (LQ-C02)» y «El descuento por pronto pago no reduce el neto (LQ-C04)», L7 a L13), en rojo hasta la Fase 2, *issue* #171; en verde, `AssessmentGoldenFixturesTest` («El descuento efectivo es el neto menos el valor a pagar con descuento (LQ-C04)»)
- [x] **006‑14** — Dados los vehículos nuevos de L14 y L15, cuando se calculan sus fechas con la configuración de Ipiales (`calendario.vehiculos_nuevos`), entonces el límite del descuento (o que no aplica), el límite sin intereses y el inicio de la mora son los de la tabla (Doc. 03 §9.7). El documento no da base ni categoría: los casos comparan solo las fechas (H2). _Prueba:_ `AssessmentGoldenTest` (método `006-14`, L14 y L15), en rojo hasta la Fase 2, *issue* #171

**Cuenta corriente**

- [x] **006‑03** — Dados los casos C1 a C9, cuando se transcriben, entonces cada uno tiene su *fixture* con la entrada, el resultado esperado y su fuente, como en 006‑01. _Prueba:_ `LedgerGoldenFixturesTest` (en verde): «Los casos de golden/ledger son exactamente C1 a C9», «Cada caso compara exactamente lo que el documento da: el estado solo en C2, sin saldos en C5», «Las entradas inferidas están declaradas con su motivo y su supuesto» y «Los movimientos que salen de una regla llevan derived_from y los demás no»; *fixtures* de `backend/src/test/resources/golden/ledger/` (PR #174)
- [x] **CA‑4.01 · LQ‑C04** — Dada la emisión del caso C1, entonces la obligación y su movimiento son los de la tabla. _Prueba:_ `LedgerGoldenTest` (método `CA-4.01`, C1), en rojo hasta la Fase 4, *issue* #173; en verde, `LedgerGoldenFixturesTest` («C1: el capital de la obligación es el neto de la emisión, sin restar el descuento (LQ-C04)» y «C1 espera en la obligación la modalidad que trae el evento de emisión (§3.1)»)
- [x] **CA‑4.02 · CA‑4.03** — Dados los pagos de C2 y C3, cuando se imputan, entonces los movimientos, el saldo y el estado son los de la tabla. _Prueba:_ `LedgerGoldenTest` («Un pago dentro del plazo por el valor con descuento salda la obligación (CA-4.02)», C2, y «Un pago parcial dentro del plazo no genera descuento (CA-4.03)», C3), en rojo hasta la Fase 4, *issue* #173; en verde, «Un descuento indebido en C3 falla aunque todos los demás movimientos coincidan»
- [x] **CA‑4.04 · IM‑01 · IM‑03** — Dadas las deudas de C4 y C5, cuando se imputa el pago de $200.000, entonces el reparto es proporcional, cada componente al peso con el residuo al impuesto, la suma imputada es $200.000 y ningún componente queda negativo. La deuda de cada componente es una **entrada** del caso: la prueba no calcula intereses ni sanciones (decisión 5, hallazgo 1). _Prueba:_ `LedgerGoldenTest` (método `CA-4.04`, C4 y C5), en rojo hasta la Fase 4, *issue* #173; en verde, `LedgerGoldenFixturesTest` («En C4 y C5 la suma imputada es el pago y ningún saldo queda negativo (IM-01, IM-03)»). Ningún ejemplo distingue el residuo al impuesto de otro redondeo por componente (plan, «Cobertura que los ejemplos no dan»), y C4 y C5 dependen del pendiente 9 del Doc. 04 §14
- [x] **CA‑4.08** — Dado el reverso de C6, entonces se reversan el pago y el descuento y el saldo vuelve a $342.000. _Prueba:_ `LedgerGoldenTest` («El reverso bancario reversa el pago y el descuento (CA-4.08)», C6), en rojo hasta la Fase 4, *issue* #173; en verde, «Solo los reversos de C6 llevan reverses, y apuntan al pago y al descuento (§6.2)»
- [x] **CA‑4.09** — Dados los pagos de C7 a C9 de una obligación de la modalidad B, cuando se distribuyen, entonces el 80 % del departamento se redondea al peso, el municipio recibe el residuo y los valores son los de la tabla. _Prueba:_ `LedgerGoldenTest` («La distribución da al departamento su parte y al municipio el residuo (CA-4.09)», C7 a C9), en rojo hasta la Fase 4, *issue* #173; en verde, «La distribución suma el dinero recibido y no incluye el descuento (§7, C8)». C7 a C9 son exactos al peso: ningún ejemplo ejerce el redondeo del 80 % (plan, «Cobertura que los ejemplos no dan»)

**Comunes**

- [x] **006‑04** — Dado el código actual, en el que no existen el motor ni la cuenta corriente, cuando se ejecutan las pruebas *golden* con el comando del `README`, que usa el parámetro `junit.jupiter.conditions.deactivate` de JUnit para que se ejecuten las pruebas marcadas con `@Disabled`, entonces **todas fallan**, y fallan porque el motor o la cuenta corriente no están implementados: ninguna falla por un *fixture* que no carga, un campo que falta o un error de la prueba. _Prueba:_ en verde, `AssessmentGoldenFixturesTest` y `LedgerGoldenFixturesTest` («Cada caso se convierte en la entrada del adaptador sin error» y «Mientras no hay motor / cuenta corriente, cada invocación en rojo falla por GoldenEngineNotImplementedException»); con el comando en rojo del `README`, las 31 invocaciones de `AssessmentGoldenTest` (PR #172) y las 9 de `LedgerGoldenTest` (PR #174) fallan con esa excepción
- [x] **006‑05** — Dados todos los *fixtures*, cuando corre el CI, entonces una prueba **en verde** comprueba que cada uno carga, tiene todos sus campos, sus montos son decimales exactos (nunca `float` ni `double`), sus códigos de regla son de la lista de esta spec y cada caso de «Casos *golden*» tiene su *fixture*. Si alguien borra o daña un *fixture*, esa prueba falla. _Prueba:_ `GoldenFixturesTest` (meta-esquema y variantes dañadas comunes) y `GoldenComparatorTest` (mutaciones de cada tipo, PR #170); `AssessmentGoldenFixturesTest` y `LedgerGoldenFixturesTest` (ids exactos, variantes del esquema de cada módulo y mutaciones de cada caso real, PR #172 y #174). Todas en verde en el CI
- [x] **006‑06** — Dada la compuerta Q02, cuando se integra la feature, entonces el CI del *backend* sigue en verde y obligatorio, ninguna prueba que ya existía se desactiva ni se debilita, y las pruebas *golden* en rojo se apartan del CI solo con `@Disabled`, cuyo motivo cita su *issue*: uno para el motor de liquidación (L1 a L15) y otro para la cuenta corriente (C1 a C9), aprobados en la H1 (decisión 1, Q02). Ninguna otra prueba lleva `@Disabled`, y no cambian el `pom.xml` ni los *workflows*. _Prueba:_ `AssessmentGoldenFixturesTest` y `LedgerGoldenFixturesTest` («Todo @Disabled … cita el mismo issue» y «… no tiene condiciones de JUnit ni extensiones que omitan casos»); `backend · verify` en verde en los PR #170, #172 y #174, cuyos diffs no desactivan otra prueba ni tocan el `pom.xml` ni los *workflows* (H3)
- [x] **006‑07** — Dado cada caso, entonces su prueba lleva en el nombre visible (en español) los códigos de regla de su fila y el número del caso, p. ej. «CA‑L04 · L9 · camioneta blindada, tabla 120.000», y como etiqueta los códigos del criterio de la spec que la ejecuta (`docs/arquitectura/04` §4.3). JUnit no permite una etiqueta distinta en cada invocación de una prueba parametrizada (ajuste de la H2, revisión de legibilidad). _Prueba:_ «… tiene un método por código de la spec, con su etiqueta y sus casos» y «El nombre visible de un caso lleva sus códigos, su id y su descripción», en `AssessmentGoldenFixturesTest` y `LedgerGoldenFixturesTest`
- [x] **006‑08** — Dado el código de las pruebas, entonces no tiene ningún valor tributario: tarifas, rangos, porcentajes, fechas y redondeo salen de la configuración de jurisdicción o del *fixture*, nunca de la clase de la prueba (CC‑01, `AGENTS.md` §4.5). _Evidencia:_ la revisión adversarial y las cuatro lentes de los PR #170, #172 y #174 (ningún valor tributario en el código Java: los valores salen de los *fixtures* y de la copia de la configuración). No hay una prueba automática para esta regla (CC‑01 se verifica en revisión, `backend/AGENTS.md`)
- [x] **006‑09** — Dados los *fixtures* y las pruebas, entonces están dentro de `backend/` y no leen nada fuera de él (IN‑01, `AGENTS.md` §4.6): la configuración que usan es la copia de Ipiales del *backend*, identificada por su SHA‑256 calculado con fin de línea LF, como en el Doc. 03 §9.2 (decisión 4). _Prueba:_ `GoldenFixturesTest` («El SHA-256 se calcula con fin de línea LF, también en Windows» y «Una ruta que sale de src/test/resources no se lee») y «Cada caso usa la copia de Ipiales por su ruta y su SHA-256» en los dos módulos
- [x] **006‑10** — Dado un *fixture*, entonces dice de qué versión del documento salió (decisión 9). _Prueba:_ «Cada caso dice de qué documento, versión, sección y fila sale», en `AssessmentGoldenFixturesTest` y `LedgerGoldenFixturesTest`
- [x] **006‑11** — Dados los ejemplos del Doc. 05 §4.3 (intereses de mora), entonces ningún *fixture* los transcribe ni cita el Doc. 05: no son *golden* hasta resolver los pendientes 2, 10, 11 y 12 del Doc. 05 (`AGENTS.md` §5.3; decisión 14 de la H1 de la feature 005). _Prueba:_ `GoldenFixturesTest` (variante dañada que cita el Doc. 05 y «Una frase que termina en «cartera.» no cita el Doc. 05»)
- [x] **006‑12** — Dadas las pruebas *golden*, entonces su `README` explica cómo ejecutarlas en rojo, cómo se ponen en verde cuando llegue el motor y qué hacer cuando cambia un ejemplo del documento (decisión 9), y el `CHANGELOG.md` del *backend* tiene su entrada. _Evidencia:_ `backend/src/test/resources/golden/README.md` (PR #170, con las secciones de liquidación y de cuenta corriente de los PR #172 y #174) y las entradas de `backend/CHANGELOG.md` de los tres PR
- [x] **006‑13** — Dado CA‑4.07, entonces el Doc. 04 lo registra como pendiente en §14 (falta la fecha del pago y el resultado esperado con las fechas de Ipiales), y también el método del redondeo «al peso» en la imputación y en la distribución, del que depende C5 (H2), con su versión subida, en un PR de documento aparte (decisión 6). _Evidencia:_ Doc. 04 v0.3: CA‑4.07 en §13 y las filas 8 y 9 de §14 (PR #169)

## Fuera de alcance

- **El motor de liquidación y la cuenta corriente**, y su diseño (puertos, tipo `Dinero` del ADR 0016, pasos del motor): son de las Fases 2 y 4. Esta feature no crea código de producción en `tax` ni en `ledger`.
- **Los ejemplos del Doc. 05 §4.3** (intereses de mora): entran como *golden* con la feature del Módulo 5, cuando se resuelvan los pendientes 2, 10, 11 y 12 del Doc. 05.
- **Los criterios sin valores numéricos**: CA‑L05 a CA‑L10, CA‑H01 a CA‑H05, CA‑4.05, CA‑4.06 y CA‑4.10 a CA‑4.18. Son pruebas de integración o de comportamiento de las features de cada opción de menú (decisión 8).
- **CA‑4.07** (decisión 6): entra como *golden* cuando el Doc. 04 fije la fecha del pago y el resultado esperado.
- **Los ejemplos de homologación** (Doc. 03 §4, art. 3 par. 3: 1.548 cc → 1.500 y 1.596 cc → 1.600): son del módulo `valuation` y de la opción 2.6, no del motor ni de la cuenta corriente. No se sumaron en la H1.
- **Corregir los documentos funcionales o la configuración** por los hallazgos de la decisión 5: esta feature los reporta; cada corrección sigue su propio proceso (versión del documento, `AGENTS.md` §3.1 y §4.3).
- **La herramienta de trazabilidad** que cruza los códigos de `docs/funcional/` con las etiquetas de las pruebas (`docs/arquitectura/04` §4.3): es de `tools/`, más adelante. Esta feature deja las etiquetas listas para ella.
- **Las pruebas de propiedades** (jqwik): son otra dependencia (ADR) y otro nivel de la estrategia de pruebas.
- **Cambiar los *workflows* de CI o el `pom.xml`**: la decisión 1 no los toca.

## Decisiones de la H1 (resueltas)

_La persona que dirige el trabajo aprobó la spec el 2026-11-11 con las nueve recomendaciones (respuesta textual: «Sí, todas (Recomendado)»), incluida la aprobación de `AGENTS.md` §4.5 para desactivar las pruebas *golden* con `@Disabled` y su *issue*._

1. **Pruebas en rojo y Q02: `@Disabled` con *issue*** (opción A). Cada clase *golden* lleva `@Disabled` con el número de su *issue* y la etiqueta `golden`: un *issue* para el motor de liquidación (L1 a L15) y otro para la cuenta corriente (C1 a C9). Los *issues* se crean en la implementación. Se ejecutan en rojo con el parámetro `junit.jupiter.conditions.deactivate` de JUnit, y la prueba en verde de 006‑05 cuida los *fixtures*. No cambian el `pom.xml` ni los *workflows*. Se descartaron la etiqueta excluida con perfil de Maven (cambia el `pom.xml`), las pruebas que esperan el fallo (no verifican nada) y las suposiciones (se omiten en silencio).
2. **Ubicación y formato.** Pruebas en `backend/src/test/java/co/com/sapenlinea/liv/tax/…` y `…/liv/ledger/…`; *fixtures* en YAML en `backend/src/test/resources/golden/tax/` y `golden/ledger/`, un archivo por tabla de origen, con los montos como decimales exactos, leídos con el cargador seguro del *backend* (`SafeYamlLoader`) y sin dependencias nuevas. El plan fija el subpaquete y cómo se reusa el cargador.
3. **Interfaz: un adaptador de prueba**, en el código de pruebas y no en `src/main`, uno por módulo, con tipos propios de los *fixtures*. Su única implementación hoy lanza «no implementado». Con el motor, solo cambia el cuerpo del adaptador. No se crean puertos ni el tipo `Dinero`.
4. **Configuración: la copia de Ipiales 1.1 del *backend*** (`src/test/resources/contracts/jsonschema/config-jurisdiccion/1.1/`), identificada en el *fixture* por su SHA‑256. Las pruebas de liquidación prueban el cálculo, no la competencia; los casos del Doc. 02 §B.9 comparan solo la tarifa, los meses y el bruto; la distribución de C7 a C9 lee el bloque de la modalidad B de la configuración.
5. **Hallazgos en los ejemplos: se transcribe el valor del documento** y el *fixture* anota de qué depende. No se corrige ningún documento ni la configuración en esta feature:
   1. los $15.000 de intereses del Doc. 04 §5.2 (C4, C5) son ilustrativos: la deuda por componente es una entrada del caso, y la sanción de $30.000 de C5 también es hipotética (en Ipiales las dos sanciones están inactivas);
   2. CA‑4.07 no da la fecha del pago: decisión 6;
   3. `redondeo.empate: HACIA_ARRIBA` tiene el comentario «POR_CONFIRMAR» en la configuración de Ipiales con la regla `redondeo` en `VIGENTE` (afecta a L8, C7 y C8);
   4. dependen de valores `POR_CONFIRMAR`: la tarifa `MOTO` (L11), `causacion.proporcionalidad_nuevos` y la fracción de mes (L4, L13; Doc. 02 §12, pendiente 2) y la sección `recaudo` (C3, C7 a C9);
   5. CA‑4.03 depende de `descuento_pago_parcial: NO_APLICA`, valor inicial del pendiente 4 del Doc. 04 §14;
   6. el Doc. 02 §B.9 no da el neto del vehículo nuevo (L4 compara solo el bruto);
   7. la precisión del bruto: decisión 7;
   8. el redondeo de medio peso en la imputación y en la distribución no está definido; ningún caso lo toca;
   9. el Doc. 03 §9.3 dice que sus liquidaciones se calcularon «con el motor de reglas», que no existe; los valores se recalcularon aparte y coinciden.
6. **CA‑4.07 queda fuera** de las *golden* y se registra como pendiente del Doc. 04 §14, en un PR de documento aparte que declara el plan (criterio 006‑13). Entra cuando el documento fije la fecha del pago y el resultado esperado.
7. **Precisión:** los valores finales (neto, descuento, a pagar, imputado, distribuido y saldos) se comparan exactos, al peso; el bruto, redondeado al centavo con la mitad hacia arriba, como lo muestran los documentos.
8. **CA‑4.xx que entran:** CA‑4.01 a CA‑4.04, CA‑4.08 y CA‑4.09, y las filas del Doc. 04 §7. Los demás son pruebas de integración de las features del Módulo 4.
9. **Documento y *fixture*:** dos PR enlazados, primero el del documento (que sube su versión) y luego el del *backend*, que cambia el *fixture* y la versión de origen que cita. El `README` de las *golden* lo explica. Corregir la redacción de `docs/arquitectura/04` §5.1 («en el mismo cambio») queda como propuesta para su propio proceso.
