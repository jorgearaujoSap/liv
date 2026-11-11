# 006 · Pruebas *golden* en rojo

**Estado:** propuesta
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `tax` y `ledger` (solo pruebas del *backend*; sin código de negocio) · **Menú:** ninguno (prepara los Módulos 3 y 4) · **Tipo de cambio:** crítico: pruebas en rutas críticas (`backend/**/liv/tax/**`, `backend/**/liv/ledger/**`) y, según la pregunta 1, el `backend/pom.xml` o la desactivación de pruebas con aprobación (`AGENTS.md` §4.3 y §4.5)
**Fuentes:** Doc. 02 v0.2 §B.6 a §B.9 y §11 (CA‑L01 a CA‑L04); Doc. 03 v0.5 §9.1, §9.3, §9.6 y §9.7; Doc. 04 v0.2 §3.1, §5.1, §5.2, §7 y §13 (CA‑4.01 a CA‑4.09); `docs/funcional/anexos/config_jurisdiccion_52356_2026.yaml` (esquema 1.1); `docs/arquitectura/00` §8.1 (F0‑11); `docs/arquitectura/04` §4.3, §5, §5.1 y §6 (Q02); `AGENTS.md` §4.5, §4.6, §5.1 y §5.3; `backend/AGENTS.md`; ADR 0016 y 0031

## Qué hace

Los documentos funcionales traen ejemplos con números: cuánto impuesto da un vehículo con tal base, cuánto se paga con descuento, cómo se reparte un pago entre impuesto e intereses. Hoy esos números solo están en el texto: nada comprueba que el sistema, cuando exista, dé lo mismo.

Con esta feature:
- cada ejemplo numérico de los Docs. 02, 03 y 04 queda **transcrito a un archivo de datos** del *backend* (un *fixture*): la entrada, la configuración que usa, el resultado esperado al peso, el código de la regla y de dónde salió (documento, versión, sección y fila);
- cada caso es una **prueba *golden***: una prueba parametrizada que le pasa la entrada al motor de liquidación o a la cuenta corriente y compara lo que devuelven con lo que dice el documento;
- como el motor (Fase 2) y la cuenta corriente (Fase 4) todavía no existen, las pruebas **fallan** («en rojo») por esa razón y solo por esa. El día que el motor exista, se ponen en verde sin reescribirlas;
- el CI del *backend* sigue en verde y obligatorio: cómo conviven las pruebas en rojo con la compuerta Q02 lo decide la pregunta 1;
- una prueba en verde comprueba que los *fixtures* están completos y bien formados, para que no se dañen mientras esperan al motor.

La feature **no** implementa el motor ni la cuenta corriente, ni decide su diseño: fija **qué** deben dar, no **cómo**.

### Casos *golden*

Valores tal como están en los documentos, recalculados aparte con aritmética exacta (Python `Decimal`): coinciden todos al peso, y el bruto al centavo (pregunta 7). Una celda vacía es un valor que el documento no da: la prueba no lo compara y la feature no lo inventa. «—» es un valor que el documento dice que no aplica.

**Liquidación (módulo `tax`)**

| Caso | Fuente | Entrada | Tarifa | Meses | Bruto | Neto redondeado | Descuento efectivo | A pagar con descuento | Fecha límite |
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
| C10 | CA‑4.07 | Pago de $350.000 sobre un recibo de $342.000 | Saldo a favor de $8.000. **Entra solo si la pregunta 6 lo resuelve** |

## Por qué

- Es el entregable **F0‑11**, la última condición que falta para cerrar la Fase 0 (ADR 0031). Sin ella no empieza ninguna feature de negocio.
- Los ejemplos de los documentos son la especificación ejecutable del motor (`docs/arquitectura/04` §5.1). Escribirlos antes que el motor obliga a que el motor se construya contra ellos (TDD), y no al revés.
- Transcribirlos ahora destapa los huecos de los ejemplos (valores ilustrativos, fechas que faltan, precisión) mientras arreglarlos solo cuesta corregir un documento.

## Criterios de aceptación

**Liquidación**

- [ ] **006‑01** — Dados los casos L1 a L13 de «Casos *golden*», cuando se transcriben, entonces cada uno tiene su *fixture* con la entrada, la referencia a la configuración que usa (pregunta 4), el resultado esperado de cada columna con valor y su fuente (documento, versión, sección y fila). Ningún *fixture* trae un valor que el documento no dé, ni otros casos que los de la tabla. _Prueba:_ pendiente
- [ ] **CA‑L01 · CA‑L02** — Dadas las bases $57.349.000 (L5) y $57.349.001 (L6), cuando se liquidan, entonces la tarifa es 1,5 % (borde superior inclusivo) y 2,5 %, y el bruto de L5 es $860.235. _Prueba:_ pendiente
- [ ] **CA‑L03** — Dado el vehículo nuevo con causación el 15‑ago‑2026 (L4 y L13), cuando se liquida, entonces tiene 5 meses gravables y bruto $1.041.666,67 (con la precisión de la pregunta 7), y con la configuración de Ipiales, neto $1.042.000, sin descuento y fecha límite 31‑dic‑2026. _Prueba:_ pendiente
- [ ] **CA‑L04** — Dada la camioneta blindada con tabla $120.000.000 (L3 y L9), cuando se liquida, entonces la base ajustada es $132.000.000 y la tarifa es la del tramo de esa base, 3,5 %, con bruto $4.620.000. _Prueba:_ pendiente
- [ ] **006‑02** — Dados los automóviles usados de L1 y L2, cuando se liquidan, entonces la tarifa y el bruto son los de la tabla ($675.000 y $2.000.000). _Prueba:_ pendiente
- [ ] **LQ‑C01 · LQ‑C02 · LQ‑C04** — Dados los casos L7 a L13 con la configuración de Ipiales, cuando se liquidan, entonces el neto redondeado, el descuento efectivo, el valor a pagar con descuento y la fecha límite son los de la tabla: redondeo al $1.000 más cercano solo al final, un $500 exacto sube (L8), el descuento es la diferencia entre el neto y el valor a pagar redondeado (Doc. 03 §9.3, nota 1) y no reduce el neto. _Prueba:_ pendiente

**Cuenta corriente**

- [ ] **006‑03** — Dados los casos C1 a C9 (y C10 según la pregunta 6), cuando se transcriben, entonces cada uno tiene su *fixture* con la entrada, el resultado esperado y su fuente, como en 006‑01. _Prueba:_ pendiente
- [ ] **CA‑4.01 · LQ‑C04** — Dada la emisión del caso C1, entonces la obligación y su movimiento son los de la tabla. _Prueba:_ pendiente
- [ ] **CA‑4.02 · CA‑4.03** — Dados los pagos de C2 y C3, cuando se imputan, entonces los movimientos, el saldo y el estado son los de la tabla. _Prueba:_ pendiente
- [ ] **CA‑4.04 · IM‑01 · IM‑03** — Dadas las deudas de C4 y C5, cuando se imputa el pago de $200.000, entonces el reparto es proporcional, cada componente al peso con el residuo al impuesto, la suma imputada es $200.000 y ningún componente queda negativo. La deuda de cada componente es una **entrada** del caso: la prueba no calcula intereses ni sanciones (pregunta 5, hallazgo 1). _Prueba:_ pendiente
- [ ] **CA‑4.08** — Dado el reverso de C6, entonces se reversan el pago y el descuento y el saldo vuelve a $342.000. _Prueba:_ pendiente
- [ ] **CA‑4.09** — Dados los pagos de C7 a C9 de una obligación de la modalidad B, cuando se distribuyen, entonces el 80 % del departamento se redondea al peso, el municipio recibe el residuo y los valores son los de la tabla. _Prueba:_ pendiente

**Comunes**

- [ ] **006‑04** — Dado el código actual, en el que no existen el motor ni la cuenta corriente, cuando se ejecutan las pruebas *golden* con el comando que documenta la feature, entonces **todas fallan**, y fallan porque el motor o la cuenta corriente no están implementados: ninguna falla por un *fixture* que no carga, un campo que falta o un error de la prueba. _Prueba:_ pendiente
- [ ] **006‑05** — Dados todos los *fixtures*, cuando corre el CI, entonces una prueba **en verde** comprueba que cada uno carga, tiene todos sus campos, sus montos son decimales exactos (nunca `float` ni `double`), sus códigos de regla son de la lista de esta spec y cada caso de «Casos *golden*» tiene su *fixture*. Si alguien borra o daña un *fixture*, esa prueba falla. _Prueba:_ pendiente
- [ ] **006‑06** — Dada la compuerta Q02, cuando se integra la feature, entonces el CI del *backend* sigue en verde y obligatorio, ninguna prueba que ya existía se desactiva ni se debilita, y las pruebas en rojo se apartan del CI solo con el mecanismo que apruebe la persona en la pregunta 1, con su *issue* (Q02). _Prueba:_ pendiente
- [ ] **006‑07** — Dado cada caso, entonces su prueba lleva en el nombre visible (en español) y como etiqueta los códigos de regla de su fila y el número del caso (`docs/arquitectura/04` §4.3), p. ej. «CA‑L04 · L9 · camioneta blindada, tabla 120.000». _Prueba:_ pendiente
- [ ] **006‑08** — Dado el código de las pruebas, entonces no tiene ningún valor tributario: tarifas, rangos, porcentajes, fechas y redondeo salen de la configuración de jurisdicción o del *fixture*, nunca de la clase de la prueba (CC‑01, `AGENTS.md` §4.5). _Prueba:_ pendiente
- [ ] **006‑09** — Dados los *fixtures* y las pruebas, entonces están dentro de `backend/` y no leen nada fuera de él (IN‑01, `AGENTS.md` §4.6): la configuración que usan es una copia del *backend*, identificada por su SHA‑256. _Prueba:_ pendiente
- [ ] **006‑10** — Dado un *fixture*, entonces dice de qué versión del documento salió (pregunta 9). _Prueba:_ pendiente
- [ ] **006‑11** — Dados los ejemplos del Doc. 05 §4.3 (intereses de mora), entonces ningún *fixture* los transcribe ni cita el Doc. 05: no son *golden* hasta resolver los pendientes 2, 10, 11 y 12 del Doc. 05 (`AGENTS.md` §5.3; decisión 14 de la H1 de la feature 005). _Prueba:_ pendiente
- [ ] **006‑12** — Dadas las pruebas *golden*, entonces su `README` explica cómo ejecutarlas en rojo, cómo se ponen en verde cuando llegue el motor y qué hacer cuando cambia un ejemplo del documento (pregunta 9), y el `CHANGELOG.md` del *backend* tiene su entrada. _Prueba:_ pendiente

## Fuera de alcance

- **El motor de liquidación y la cuenta corriente**, y su diseño (puertos, tipo `Dinero` del ADR 0016, pasos del motor): son de las Fases 2 y 4. Esta feature no crea código de producción en `tax` ni en `ledger`.
- **Los ejemplos del Doc. 05 §4.3** (intereses de mora): entran como *golden* con la feature del Módulo 5, cuando se resuelvan los pendientes 2, 10, 11 y 12 del Doc. 05.
- **Los criterios sin valores numéricos**: CA‑L05 a CA‑L10, CA‑H01 a CA‑H05, CA‑4.05, CA‑4.06 y CA‑4.10 a CA‑4.18. Son pruebas de integración o de comportamiento de las features de cada opción de menú (pregunta 8).
- **Los ejemplos de homologación** (Doc. 03 §4, art. 3 par. 3: 1.548 cc → 1.500 y 1.596 cc → 1.600): son del módulo `valuation` y de la opción 2.6, no del motor ni de la cuenta corriente. Se pueden sumar si la persona lo pide en la H1.
- **Corregir los documentos funcionales o la configuración** por los hallazgos de la pregunta 5: esta feature los reporta; cada corrección sigue su propio proceso (versión del documento, `AGENTS.md` §3.1 y §4.3).
- **La herramienta de trazabilidad** que cruza los códigos de `docs/funcional/` con las etiquetas de las pruebas (`docs/arquitectura/04` §4.3): es de `tools/`, más adelante. Esta feature deja las etiquetas listas para ella.
- **Las pruebas de propiedades** (jqwik): son otra dependencia (ADR) y otro nivel de la estrategia de pruebas.
- **Cambiar los *workflows* de CI**: ninguna opción recomendada los toca.

## Preguntas abiertas

_Para la H1. Cada una trae su recomendación; ninguna se resuelve con un supuesto._

1. **Cómo conviven las pruebas en rojo con Q02** (CI 100 % en verde; prohibido desactivar pruebas sin *issue* y aprobación). Cualquier camino aparta pruebas del CI, así que todos necesitan tu aprobación (`AGENTS.md` §4.5) y un *issue*:

   | Opción | Cómo | A favor | En contra |
   |---|---|---|---|
   | A. `@Disabled` con *issue* | Cada clase *golden* lleva `@Disabled("issue #N: motor no implementado")` y la etiqueta `golden`. Se ejecutan en rojo con un parámetro de JUnit que desactiva esa condición (comando en el `README`; el plan verifica que la versión de JUnit del proyecto lo admita, y si no, quitar la anotación en local) | Es lo que Q02 prevé (desactivar con *issue* y aprobación). No toca el `pom.xml` ni el CI | Hay que acordarse de quitar la anotación; el informe del CI las muestra como omitidas |
   | B. Etiqueta excluida y perfil de Maven | Etiqueta `golden-rojo`; el `pom.xml` la excluye de la ejecución normal y un perfil `-Pgolden` la corre | Se corren en rojo con un comando de Maven corriente | Cambia el `backend/pom.xml` (ruta crítica) y lo que ejecuta el CI |
   | C. Pruebas que esperan el fallo | Cada caso afirma que el motor lanza «no implementado» | El CI las cuenta en verde | No comparan ningún valor: son pruebas que no verifican nada (`docs/arquitectura/04` §4.2) y el día del motor hay que reescribirlas |
   | D. Suposiciones (`assumeTrue`) | Si el motor lanza «no implementado», la prueba se aborta | Se ponen en verde solas cuando llega el motor | Se omiten en silencio: es desactivar sin decirlo, y un *stub* olvidado las deja omitidas para siempre |

   **Recomendación: A**, con un *issue* para el motor (L1 a L13) y otro para la cuenta corriente (C1 a C10), más la prueba en verde de 006‑05 para que los *fixtures* no se dañen. Es el mecanismo que Q02 ya contempla y no cambia el `pom.xml` ni los *workflows*. Si prefieres B (un perfil de Maven, que cambia el `pom.xml`), dilo en la H1.

2. **Dónde viven las pruebas y los *fixtures*, y en qué formato.** **Recomendación:**
   - pruebas en `backend/src/test/java/co/com/sapenlinea/liv/tax/…` (liquidación) y `…/liv/ledger/…` (cuenta corriente). El subpaquete exacto (`domain` u otro) lo decide el plan según lo que admita `ModuleCatalogTest`;
   - *fixtures* en `backend/src/test/resources/golden/tax/` y `golden/ledger/`, un archivo por tabla de origen (p. ej. `doc02-b9.yaml`, `doc03-9-3.yaml`, `doc04-5-2.yaml`);
   - formato **YAML**, el mismo de la configuración: admite comentarios con la fuente, una persona funcional lo lee y lo compara con el documento, y se carga con el cargador seguro que ya tiene el *backend* (`SafeYamlLoader`, que lee los decimales como `BigDecimal`). Los montos van como decimales exactos. JSON no admite comentarios; CSV no sirve para los casos de la cuenta corriente, que tienen varios movimientos. El plan resuelve cómo reusar el cargador (hoy privado del paquete `contracts` de las pruebas), sin dependencias nuevas.

3. **Contra qué interfaz se escriben, si el motor no existe.** **Recomendación:** contra un **adaptador de prueba**, en el código de pruebas, no en `src/main`: una interfaz por módulo («liquida este caso», «aplica estos hechos a la cuenta corriente») con tipos propios de los *fixtures*, cuya única implementación hoy lanza «no implementado». Así no se decide ni se crea en `src/main` nada del diseño del motor de la Fase 2 (ni puertos ni el tipo `Dinero`). Cuando exista el motor, solo se reescribe el cuerpo del adaptador para llamarlo; los *fixtures* y las aserciones no cambian. Un puerto del dominio en `src/main` con un *stub*, la otra opción, adelantaría el diseño del motor en una ruta crítica.

4. **Qué configuración de jurisdicción usan.** **Recomendación:** la de Ipiales, esquema 1.1, desde la copia que ya tiene el *backend* (`src/test/resources/contracts/jsonschema/config-jurisdiccion/1.1/`, SHA‑256 en el *fixture*), sin otra copia ni valores sueltos. Lo que eso implica:
   - las pruebas de liquidación prueban el **cálculo** (base, ajustes, tarifa, meses, bruto, redondeo, descuento), no la competencia: con A + C, Ipiales solo liquida el internado (Doc. 03 §9.3), y las otras filas «muestran el cálculo que aplicaría» con B. Probar el cálculo sin la competencia evita una configuración A + B, que exigiría los datos del convenio (CJ12);
   - los casos del Doc. 02 §B.9 son de «régimen nacional», y las tarifas de Ipiales son las nacionales del Decreto 1457/2025: con la misma configuración, comparan solo la tarifa, los meses y el bruto, que es lo que da el documento;
   - la distribución de C7 a C9 (modalidad B) lee los porcentajes del bloque de B de la configuración, que existe aunque B esté inactiva; la obligación de entrada es de la modalidad B.

   La otra opción, escribir en cada *fixture* las tarifas y el redondeo del ejemplo, duplica valores tributarios fuera de la configuración y se puede desincronizar de ella.

5. **Contradicciones, valores ilustrativos y dependencias de pendientes en los ejemplos.** Se reportan; esta feature no los resuelve. Para cada uno, la recomendación es transcribir el valor del documento tal como está y anotar en el *fixture* de qué depende:
   1. **Los $15.000 de intereses del Doc. 04 §5.2** (C4, C5) son ilustrativos: con las reglas del Doc. 05 darían unos $19.000. **Recomendación:** la deuda por componente es una entrada del caso, y el caso prueba solo la imputación proporcional (como dice el propio ejemplo). La sanción de $30.000 de C5 también es hipotética: en Ipiales las dos sanciones están inactivas.
   2. **CA‑4.07 no da la fecha del pago** (C10): véase la pregunta 6.
   3. **El empate del redondeo** (L8: $2.011.500 → $2.012.000, que también arrastra C7 y C8): `redondeo.empate: HACIA_ARRIBA` tiene el comentario «POR_CONFIRMAR» en la configuración de Ipiales, aunque la regla `redondeo` está `VIGENTE` y el Doc. 03 §9.1 lo da por definido.
   4. **Dependen de valores `POR_CONFIRMAR` de la configuración:** la tarifa `MOTO` (L11), `causacion.proporcionalidad_nuevos` y la fracción de mes (L4, L13; Doc. 02 §12, pendiente 2) y la sección `recaudo` (C3, C7 a C9).
   5. **CA‑4.03 depende de un pendiente:** `descuento_pago_parcial: NO_APLICA` es el valor inicial del Doc. 04 §14, pendiente 4.
   6. **El Doc. 02 §B.9 no da el neto** del vehículo nuevo (L4): «Se redondea según LQ‑C02», que el Doc. 02 marca como pendiente de confirmar. El Doc. 03 lo da con la configuración de Ipiales (L13). L4 compara solo el bruto.
   7. **La precisión del bruto** ($1.041.666,67 frente a 1.041.666,666667 interno): véase la pregunta 7.
   8. **El modo de redondeo al peso** de la imputación («al peso, residuo al impuesto», Doc. 04 §5.1) y de la distribución («el 80 % se redondea al peso», §7) no dice qué pasa con medio peso. Ningún ejemplo cae en medio peso, así que los casos no lo prueban ni lo suponen.
   9. **El Doc. 03 §9.3 dice que las liquidaciones fueron «calculadas con el motor de reglas»**, que no existe: los valores se recalcularon aparte para esta spec y coinciden, pero el origen que cita el documento no es verificable.

6. **CA‑4.07 (C10): ¿entra, y con qué fecha?** El criterio no da la fecha del pago, y con las fechas de Ipiales ninguna lectura da el resultado de forma limpia:
   - si se paga hasta el 30‑abr, el recibo vale $308.000, no $342.000 (Doc. 04 §4.1), y como lo pagado supera el valor con descuento, se reconocería el descuento (§5.1, paso 2): el saldo a favor sería $42.000, no $8.000. Además, la regla «diferencia entre el neto y lo pagado» daría un descuento negativo en este caso;
   - si se paga después del 30‑abr, la obligación está en mora (el límite de descuento y el límite sin intereses son el mismo día) y habría intereses que el ejemplo no da;
   - un recibo de $342.000 sin mora solo existe si el límite sin intereses es posterior al del descuento, que no es el caso de Ipiales.

   **Recomendación:** dejar C10 fuera de las *golden* hasta que se fije la fecha y el resultado esperado en el Doc. 04 (con su versión), y registrarlo como pendiente de ese documento. Si prefieres incluirlo ahora, dime la fecha del pago y el saldo a favor esperado.

7. **Con qué precisión se compara el bruto.** El Doc. 02 §B.9 da $1.041.666,67 (dos decimales); LQ‑C01 calcula con al menos 6 decimales internos, y `docs/arquitectura/04` §5 pide «resultado esperado al peso». **Recomendación:** los valores finales (neto, descuento, a pagar, imputado, distribuido, saldos) se comparan **exactos, al peso**; el bruto, que es un valor intermedio, se compara redondeado al centavo (mitad hacia arriba), que es como lo muestran los documentos. Ningún ejemplo da más decimales.

8. **Qué CA‑4.xx entran.** **Recomendación:** solo los que dan valores: CA‑4.01 a CA‑4.04, CA‑4.08 y CA‑4.09 (más CA‑4.07 según la pregunta 6), y las filas de la tabla del Doc. 04 §7. Los demás (CA‑4.05, 4.06, 4.10 a 4.13 y 4.18) prueban comportamiento (rechazos, bandejas, idempotencia, plazos de giro, formatos) y son pruebas de integración de las features de cada opción del Módulo 4.

9. **Cómo se mantienen sincronizados el documento y el *fixture*.** `docs/arquitectura/04` §5.1 pide actualizar el documento **y** el archivo *golden* «en el mismo cambio», pero un PR toca un solo proyecto y el *backend* no lee `docs/` (`AGENTS.md` §4.6). **Recomendación:** dos PR enlazados, primero el del documento (que sube su versión) y después el del *backend*, que cambia el *fixture* y su versión de origen. El `README` de las *golden* lo explica, y la H3 del PR del documento verifica que exista el del *backend*. Corregir la redacción de `docs/arquitectura/04` §5.1 queda como propuesta para su propio proceso (no se edita como efecto colateral, `AGENTS.md` §4.5).
