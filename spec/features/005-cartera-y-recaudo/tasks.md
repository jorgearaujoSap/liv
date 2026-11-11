# 005 · Cartera y recaudo: documentación funcional, configuración y esquema 1.1 — Tareas

_Checklist accionable derivada del `plan.md` (aprobado en H2, 2026-11-09). Tareas pequeñas y concretas; cada una cabe en un PR revisable (≤ ~400 líneas, salvo las dos que el plan justifica: R1 y B1). Marca `[x]` al completarlas. Cada tarea indica:_

- _su proyecto, su PR del plan y los archivos que puede tocar;_
- _de qué otras tareas depende;_
- _qué recursos compartidos usa: un contrato, los archivos de *build* de su proyecto, los anexos;_
- _la prueba o verificación que se escribe primero (TDD: rojo → verde → refactorizar). En las tareas de documentación, la «prueba primero» es la verificación de «Verificaciones por PR» del plan: se escribe antes que el texto, como lista de comprobación o *script* en el *scratchpad* de la sesión (no se sube), y se corre al final._

_Con eso el orquestador decide qué corre en paralelo (`liv-orquestador`)._

Es un cambio crítico: antes de cada PR, `liv-revision-adversarial` con dos revisores de nivel alto y las cuatro lentes de `liv-revision-4r`, con el resumen en la descripción del PR; antes de cada *commit*, la lente de legibilidad (plan, «Verificaciones por PR»). Un PR toca un solo proyecto (`AGENTS.md` §4.6). Las sesiones de las tareas **no escriben** `ESTADO.md`, el *roadmap*, el glosario, las marcas de esta lista ni los criterios de `spec.md`: se lo reportan al orquestador (`AGENTS.md` §7, ADR 0033).

## Mapa de los ocho PR

| PR | Rama | Proyecto | Tareas | Depende para **escribirse** | Depende para **integrarse** |
|---|---|---|---|---|---|
| R2 · ERD 2.2 | `feat/005-erd` | raíz | T1, T2 | nada | nada (va primero) |
| R0 · ADR 0034 | `feat/005-adr-0034` | raíz | T3, T4 | nada | nada (H3 de R0: la persona acepta el ADR) |
| B0 · Pruebas por versión | `feat/005-pruebas-por-version` | `backend/` | T5, T6 | R0 integrado | R0 |
| R3b · Anexos | `feat/005-anexos` | raíz | T7, T8, T9 | nada (se escribe primero) | **B1** (justo después) |
| R1 · Doc. 05 | `feat/005-doc05` | raíz | T10 | T2 terminada (nombres del ERD 2.2) | R2 y la **H3 de R3b** (T9); justo antes de R3a |
| R3a · Doc. 04, Doc. 03 y Excel | `feat/005-recaudo-doc` | raíz | T11 a T14 | T10 (T11 a T13; T11 además espera a T12); la H3 de R3b para T14 | R1 y la **H3 de R3b** |
| B1 · Esquema 1.1 | `feat/005-esquema-1-1` | `backend/` | T15 a T17 | la H3 de R3b (T9) y B0 integrado | B0; justo antes de R3b |
| R4 · Reglas y referencias | `feat/005-referencias` | raíz | T18, T19 | T10 y T11 a T14 terminadas | R3b, R1 y R3a |

### Orden de integración (obligatorio; plan, «Orden entre proyectos»)

1. `R2`, y en paralelo `R0 → B0`.
2. **H3 de R3b aprobada** (T9): desde ahí R3b no cambia.
3. `R1 → R3a`, seguidos (se citan entre sí: los dos lo declaran en su descripción).
4. `B1 → R3b → R4`.
5. El cierre del orquestador (*roadmap*, glosario, `ESTADO.md`, criterios de `spec.md`).

### Paralelismo (máximo tres sesiones de trabajo a la vez)

- **Ola 1:** R2 (T1, T2), R0 (T3, T4) y R3b (T7 a T9). No comparten archivos ni recursos. R3b va primero porque R3a (T14) y B1 parten de sus YAML; R1 solo la espera para integrarse, no para escribirse.
- **Ola 2**, cuando R0 esté integrado, R2 tenga T2 terminada y R3b tenga su H3 (T9): B0 (T5, T6), R1 (T10) y R3a (T11 a T14). Los tres tocan archivos distintos y proyectos distintos. R1 y R3a no comparten archivos, salvo que T14 agrega sus filas al Doc. 05 §14, que crea T10: T14 espera a que T10 esté terminada, y T11 a T13 pueden correr a la vez que T10. Las filas de R4 (T19) van después.
- **Ola 3**, cuando B0 esté integrado y R3b tenga su H3: B1 (T15 a T17) y, a la vez, R4 (T18, T19), que es de la raíz y no toca el backend. B1 no se corre junto a otra tarea de `backend/` que toque los archivos de *build* o las pruebas de `contracts/` (recurso compartido).
- **En serie siempre:** T1 → T2; T3 → T4; T5 → T6; T7 → T8 → T9; T15 → T16 → T17; B0 → B1 (mismo contrato y mismas pruebas).
- **F0‑11 (pruebas *golden*, Fase 0; ver `../../constitution/roadmap.md`) puede correr a la vez en `backend/`**, porque no toca los mismos archivos, salvo `backend/CHANGELOG.md`, que se comparte (plan, «Dependencias y recursos»).

### Recursos compartidos

| Recurso | Quién lo escribe | Quién lo usa | Regla |
|---|---|---|---|
| Contrato JSON Schema `config-jurisdiccion` (`backend/contracts/jsonschema/config-jurisdiccion/`) y sus pruebas | B0 (mueve las pruebas), B1 (agrega la 1.1) | B0 y B1 | En serie. `1.0.json` y `1.0.casos.json` no cambian nunca (005‑16) |
| Archivos de *build* y `backend/CHANGELOG.md` | B0, B1 | B0, B1, F0‑11 (solo el `CHANGELOG.md`) | En serie entre B0 y B1; ninguno toca `pom.xml` |
| YAML de los anexos (`docs/funcional/anexos/config_jurisdiccion_*.yaml`) | R3b (T7, T8) | B1 los copia (T15), R3a los describe y los evalúa (T13, T14), R4 no los toca | **Congelados desde la H3 de R3b (T9).** Si la H3 pide cambiarlos, se cambian antes de integrar B1 y R3a vuelve a evaluar su §9.2 (T14). Cambiar su forma después de integrar B1 es una versión 1.2 |
| Doc. 05 | R1 (T10) | R3a (T14) y R4 (T19) solo agregan filas a su §14 | En serie, en el orden de integración |
| ERD | R2 (T1, T2) | R1 lo cita | Solo R2 lo edita |
| Doc. 03, Doc. 04 y `Formatos_Intercambio_Recaudo.xlsx` | R3a | R4 no los toca | Solo R3a |

## PR R2 · ERD 2.2 (raíz, rama `feat/005-erd`) — 005‑15

- [x] **T1** · ERD 2.2, parte 1: encabezado y esquema `ledger` (de 7 a 19 tablas), con el aislamiento por administración (plan, paso 1).
  - **Proyecto:** raíz · **PR:** R2
  - **Archivos:** `docs/funcional/ERD_impuesto_vehicular_definitivo_v2_CSV_cartera.md` (solo el encabezado y el esquema `ledger`: el diagrama Mermaid, la lista de tablas, las restricciones, las relaciones externas, la nota de `v_obligation_balance` y la regla de `administration_id`).
  - **Depende de:** ninguna.
  - **Recursos:** el ERD (solo R2 lo edita).
  - **Prueba primero:** lista de comprobación escrita antes del texto, con la fila de R2 de «Verificaciones por PR». Cada punto tiene que aparecer en el Mermaid, en la lista de tablas y en las relaciones:
    - las 12 tablas nuevas: `payment_receipt`, `payment_receipt_line`, `bank_file`, `bank_file_row`, `bank_reconciliation`, `distribution_batch`, `participation_report`, `participation_report_row`, `participation_match`, `participation_claim`, `credit_balance_use` y `movement_type`;
    - cada uno de los nueve puntos del Doc. 04 §11;
    - el signo por tipo (UNIQUE del catálogo, `movement_sign`, FK compuesta y CHECK local), `payment.idempotency_key` UNIQUE y el CHECK de `tax_obligation.assessment_id` (nulo solo con `origin = MIGRACION`);
    - las columnas nuevas de `tax_obligation`, `payment`, `revenue_distribution` y `credit_balance` (`available_amount` pasa a `original_amount`, P16);
    - `v_obligation_balance` como vista normal con `security_invoker` (P19);
    - las cuatro tablas de `ledger` sin `administration_id` (`obligation_component`, `tax_account_movement`, `payment_allocation`, `revenue_distribution`) lo reciben con FK compuesta y RLS (P19, opción (a)); las dos excepciones (`ledger.movement_type` y `portfolio.interest_rate`); y la FK `interest_calculation.account_movement_id`, validada en la transacción y dicha en el ERD;
    - la segregación (CHECK de personas distintas en `bank_file`, `bank_reconciliation`, `distribution_batch` y `participation_report`) y UNIQUE por administración y SHA‑256 en `bank_file` y `participation_report` (CA‑4.05).
- [x] **T2** · ERD 2.2, parte 2: esquema `portfolio` (de 11 a 13 tablas), `tax.penalty_rule` y cuadre del número de tablas (plan, paso 1).
  - **Proyecto:** raíz · **PR:** R2
  - **Archivos:** `docs/funcional/ERD_impuesto_vehicular_definitivo_v2_CSV_cartera.md` (esquema `portfolio`, `tax.penalty_rule`, encabezado con las 79 tablas y el motivo de la versión, y relaciones externas).
  - **Depende de:** T1 (mismo archivo).
  - **Recursos:** el ERD.
  - **Prueba primero:** lista de comprobación de R2 para el Doc. 05 §10, escrita antes del texto:
    - `interest_rate`: `administration_id` nulo para la tasa nacional, `usury_rate`, `support_pdf_sha256`, `status`, `uploaded_by` y `approved_by` con CHECK, índice único parcial por tipo y `valid_from` solo para `APROBADA`, y sin `annual_effective_rate`;
    - `interest_calculation`: columnas nuevas, `reversed_by_movement_id`, índice único parcial `WHERE reversed_by_movement_id IS NULL AND reason <> 'SIMULACION'` y el orden de la transacción de recálculo, sin `interest_rate_id` ni `applied_annual_rate` en la cabecera;
    - `interest_calculation_detail.interest_rate_id`; `prescription_event`, `prescription_evaluation`, `collection_portfolio_snapshot` (particionada por fecha) y `opening_balance_row`;
    - tablas nuevas `portfolio_cutoff` (CHECK `published_by <> generated_by`) y `opening_balance_event`;
    - `tax.penalty_rule`: `rule_code`, `active`, `applies_if`, `tax_cap_rate`, `minimum_uvt` y `status`;
    - el número de tablas: 65 + 12 (`ledger`) + 2 (`portfolio`) = **79**, y cada tabla nueva con su regla de `administration_id`.
  - **Al terminar T1 y T2 (≈ 380 líneas en total):** revisión adversarial, las cuatro lentes y el PR (H3).

## PR R0 · ADR 0034, «Casos comunes por versión» (raíz, rama `feat/005-adr-0034`) — P15 y P18

- [x] **T3** · Redactar el ADR 0034 como «Propuesto» y su fila en la tabla de ADR (plan, paso 4; *skill* `liv-adr`).
  - **Proyecto:** raíz · **PR:** R0
  - **Archivos:** `docs/adr/ADR-0034-casos-comunes-por-version.md` (nuevo, ≈ 60 líneas) y `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` (solo la fila 0034 de la tabla de §6).
  - **Depende de:** ninguna.
  - **Recursos:** ninguno (el ADR 0032 no se edita).
  - **Prueba primero:** la verificación de R0: el ADR sigue la plantilla de `liv-adr` (MADR) y el formato de los ADR 0032 y 0033; **complementa** al 0032 sin cambiar su decisión (lo cita, no lo reemplaza); dice la regla de P18 («los casos de la versión X se evalúan con las versiones publicadas hasta X», también en el validador de la 1.5.9) y que el caso «versión desconocida» de la 1.0 usa `"1.1"`; estado «Propuesto»; la fila del doc. 00 §6 coincide con el estilo de las filas 0032 y 0033.
- [x] **T4** · Aceptación formal del ADR 0034 en la H3 de R0.
  - **Proyecto:** raíz · **PR:** R0
  - **Archivos:** `docs/adr/ADR-0034-casos-comunes-por-version.md` y la fila 0034 del doc. 00 §6 (solo el estado y la fecha).
  - **Depende de:** T3, y de que la persona acepte el ADR (`liv-adr`: solo un humano lo acepta; la sesión no lo decide).
  - **Recursos:** ninguno.
  - **Prueba primero:** el estado del ADR y el de la fila dicen lo mismo («Aceptado», con la fecha que da la persona); el PR no se integra con el ADR en «Propuesto». **B0 espera a este PR integrado.**

## PR B0 · Pruebas de esquema por versión (`backend/`, rama `feat/005-pruebas-por-version`) — prepara 005‑16 a 005‑18

Refactorización: no cambia ningún comportamiento, y las pruebas de la 1.0 siguen iguales y en verde. **Antes de la primera línea:** correr `./mvnw verify` en `backend/` y anotar el número de pruebas (el «antes»).

- [x] **T5** · Copias de los anexos por versión y carga de casos limitada por versión (plan, paso 5, primeros puntos y P18).
  - **Proyecto:** `backend/` · **PR:** B0
  - **Archivos:** en `backend/src/test/resources/contracts/jsonschema/config-jurisdiccion/`: las dos copias de los anexos 1.0, con `git mv` a `1.0/` (T5 las mueve, no las modifica); en `backend/src/test/java/co/com/sapenlinea/liv/contracts/`: `TestConfigurations.java` (recibe la versión), `CommonCases.java` (`load` recibe la versión del archivo de casos), `JurisdictionConfigSchemas.java` (`forVersion` recibe la versión de los casos y solo resuelve las publicadas hasta ella), `JurisdictionConfigSchemaTest.java` (usa `TestConfigurations.load` con versión), `SafeYamlLoaderTest.java` y `JurisdictionConfigCommonCasesTest.java` (hoy tienen fijo `"1.0"`: pasan a recorrer cada archivo de casos, con sus casos de carga y de validación).
  - **Depende de:** T4 (ADR 0034 integrado).
  - **Recursos:** contrato `config-jurisdiccion` y sus pruebas; archivos de *build* (no se tocan).
  - **Prueba primero:** `./mvnw verify` con el mismo número de pruebas que el «antes», todas en verde; los casos de carga y de validación de `1.0.casos.json` siguen corriendo. Se corre tras mover las copias y antes de tocar el código, para ver el fallo por la ruta, y se vuelve a correr al terminar. B0 **no** demuestra P18 (con una sola versión, el registro limitado y el completo dan lo mismo): lo demuestra T15.
- [x] **T6** · Oráculo, listas cerradas y rutas de enumerados por versión, documentación y registro (plan, paso 5, puntos 2 y 3).
  - **Proyecto:** `backend/` · **PR:** B0
  - **Archivos:** en `backend/src/test/java/co/com/sapenlinea/liv/contracts/`: `JurisdictionConfigFieldTable.java` y `JurisdictionConfigFieldClasses.java` (por versión, con la tabla de la 1.0 tal como está; `CLOSED_LISTS` pasa de `Map.of` a `Map.ofEntries`), `JurisdictionConfigSchemaMutationTest.java` (el oráculo por versión; `closedListsAreExact` lee el esquema de cada versión) y `JsonSchemaFilesTest.java` (`ENUM_PATHS` por versión, con las rutas de la 1.0 sin cambio); `backend/README.md` (la sección del esquema y de las pruebas: la ruta de las copias ahora termina en `1.0/`); `backend/CHANGELOG.md` («Sin publicar»).
  - **Depende de:** T5.
  - **Recursos:** contrato `config-jurisdiccion` y sus pruebas; `backend/CHANGELOG.md` (compartido con B1 y F0‑11).
  - **Prueba primero:** las mismas del «antes»: mismo número de pruebas y todas en verde, `jsonschema.sh check` sin cambios en la 1.0, y ningún archivo de `1.0.json` ni de `1.0.casos.json` modificado (`git diff --stat` sin esos dos). Cuenta de pruebas antes y después en la descripción del PR (riesgo «la refactorización debilita una prueba»). Es un cambio de las pruebas de esquema (`AGENTS.md` §4.5): aprobado en la H2, no las debilita.
  - **Al terminar T5 y T6 (≈ 300 líneas, casi todo movimientos):** revisión adversarial, las cuatro lentes y el PR (H3).

## PR R3b · Anexos (raíz, rama `feat/005-anexos`) — 005‑11 a 005‑13

Se escribe primero y se revisa en paralelo con R3a. Se integra **después de B1**. Todo lo que los YAML llevan sale de la tabla «Valores de la plantilla» del plan.

- [x] **T7** · YAML de Ipiales (plan, paso 6).
  - **Proyecto:** raíz · **PR:** R3b
  - **Archivos:** `docs/funcional/anexos/config_jurisdiccion_52356_2026.yaml`.
  - **Depende de:** ninguna.
  - **Recursos:** los YAML de los anexos (los escribe esta tarea; B1 y R3a parten de ellos).
  - **Prueba primero:** lista de comprobación escrita antes del texto: `esquema_version: "1.1"`; las cuatro normas (`ET_ART_634_635`, `ET_ART_641`, `ET_ART_644`, `ET_ART_817_818`), la descripción de `ET_ART_634_635` sin dar por hecha la fórmula; `recaudo` con `canales: [BANCO_CONVENIO, VENTANILLA]`, `bancos` (los cuatro, con `cuenta_recaudo: PENDIENTE`), `estado_bancos: VIGENTE`, `archivo_banco` con `formato_preferido`, `formato_alterno`, `formato_por_banco: []` y `estado`, y **sin** `formato`, `imputacion.sin_indicacion` y `reporte_participacion_20`, **sin** `pasarela` ni `PASARELA` (005‑13); `cartera` con `intereses_mora`, `sanciones` (con `sistema_determinacion: LIQUIDACION_FACTURA`), `prescripcion` (con `competente: PENDIENTE` y el comentario de `reinicio` en `POR_CONFIRMAR`, pendiente 18), `cartera_historica` y `antiguedad`, **sin** `tabla_tasas` (P5); montos en pesos, tasas como fracción y `sancion_minima_uvt` en UVT (005‑12); `cartera.sanciones.uvt` e `intereses_mora` en `POR_CONFIRMAR` (005‑12 y decisión 6 de la H1; el plan los da por «igual que Ipiales» en la plantilla), y ningún `PENDIENTE` ni `POR_CONFIRMAR` pasa a `VIGENTE`; comentario con las tres opciones de `redondeo_causacion.metodo` (P8).
- [x] **T8** · YAML de la plantilla y comprobación de que los dos tienen las mismas claves (plan, paso 6; riesgo «plantilla e Ipiales se separan»).
  - **Proyecto:** raíz · **PR:** R3b
  - **Archivos:** `docs/funcional/anexos/config_jurisdiccion_plantilla_2026.yaml`.
  - **Depende de:** T7.
  - **Recursos:** los YAML de los anexos.
  - **Prueba primero:** un *script* de comprobación (en el *scratchpad*; se corre con `python -I` y los dos archivos como argumentos) que compara las claves de las secciones nuevas de los dos YAML y falla con el nombre de la clave que difiere. Se corre antes de escribir la plantilla, para verlo fallar. Además, la tabla «Valores de la plantilla»: la plantilla repite los valores de Ipiales salvo estas tres desviaciones, aprobadas en la H2 y marcadas en el archivo:
    - `estado_bancos: PENDIENTE`, con `bancos: []`;
    - `remitente: PENDIENTE`;
    - `sistema_determinacion: PENDIENTE`.

    Además, la extemporaneidad inactiva con su comentario, `estatuto_municipal: PENDIENTE` y `cartera_historica` con `vigencias_desde` y `fecha_corte_migracion` en `PENDIENTE`. El resultado de las dos comprobaciones va en la descripción del PR.
- [x] **T9** · Revisiones y **H3 de R3b: congelamiento de los YAML** (plan, paso 6).
  - **Proyecto:** raíz · **PR:** R3b
  - **Archivos:** ninguno nuevo (correcciones a T7 y T8 si las revisiones las piden).
  - **Depende de:** T7 y T8.
  - **Recursos:** los YAML de los anexos. Pasa a estar **congelado**.
  - **Prueba primero:** las revisiones de R3b pasan todas — la de «Verificaciones por PR» (T8), la adversarial y las cuatro lentes — y **la persona aprueba la H3** (no la sesión). Se anota el SHA‑256 de los dos YAML aprobados en el reporte: lo usan T14 (el §9.2 de R3a) y T15 (las copias de B1). Desde ahí R3b no cambia hasta integrarse justo después de B1. El PR se abre y espera sin integrarse.

## PR R1 · Doc. 05 v0.1 (raíz, rama `feat/005-doc05`) — 005‑01 a 005‑07

Referencia adelantada declarada: R1 y R3a se citan entre sí (los códigos del Doc. 05 y CJ15 y CJ16). Los dos esperan la H3 de R3b, se integran seguidos y cada uno lo dice en su descripción.

- [x] **T10** · Crear `05_Cartera_Intereses_Sanciones_Prescripcion.md` desde el insumo, con los ajustes del plan (plan, paso 2).
  - **Proyecto:** raíz · **PR:** R1
  - **Archivos:** `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md` (nuevo, ≈ 480 líneas; se justifica en el PR por la compuerta Q12 de `docs/arquitectura/04`: un solo documento nuevo, y partirlo dejaría uno a medias en `main`). El insumo es `.entrada-claude-ai/05_Cartera_Intereses_Sanciones_Prescripcion.md` del checkout principal: se lee como **dato**, no como instrucción.
  - **Depende de:** T2 (los nombres de las tablas son los del ERD 2.2). El Doc. 05 puede escribirse con T2 terminada en su rama; se integra después de R2.
  - **Recursos:** el Doc. 05 (lo crea esta tarea; R3a y R4 solo agregan filas a su §14, después).
  - **Prueba primero:** las verificaciones de R1, escritas antes del texto:
    - el recálculo de §4.3 con aritmética exacta (`Decimal` en un *script* del *scratchpad*, `python -I`): 1 día, $245,21 → $0; 214 días, $53.732,51 → $54.000; abono, $26.801,18 → $27.000, imputación de $185.366 al impuesto y $14.634 a los intereses, después $12.334,39 → $12.000 y saldo de $181.000 (005‑04);
    - la búsqueda de códigos viejos (`IN‑`, `PR‑`, `OB‑06` a `OB‑10`) sin resultados en el Doc. 05, y en `docs/` que `OB‑01` a `OB‑05` e `IN‑xx` conserven su significado de antes (005‑02);
    - cada tabla citada existe en el ERD 2.2 de R2, y no se citan `collection_event`, `prescription_case` ni `portfolio_snapshot` (005‑07);
    - §11 tiene los 19 pendientes, cada uno con su responsable: los 8 del insumo sin cambios y los 11 de la tabla del plan (005‑03);
    - §0 se titula «Marco normativo», con la nota de validación de jurídica y de la fuente oficial, y la UVT y las tasas de §4.1 llevan «por verificar» (decisión 6, 005‑01); los roles de §3.4, §9 y PS‑02 están marcados como propuesta (005‑06); §4.3 no cita `scripts/intereses_mora.py` y CA‑5.03 aclara que sus valores son los de un cálculo único; §5.1 dice que las reglas se materializan en `tax.penalty_rule` (P9); los ejemplos de §6.3 son ilustrativos y dicen que usan `FECHA_LIMITE_PAGO` mientras Ipiales tiene `FECHA_EJECUTORIA_FACTURA` (005‑05); CA‑5.13 dice a qué publicaciones se aplican CJ15 y CJ16 (P17);
    - el §14 nuevo («Cambios aplicados a otros documentos») lista solo los cambios que ya están en `main` o llegan en un PR que se integra antes: el ERD (R2).
  - **Al terminar:** revisión adversarial, las cuatro lentes (el insumo se compara con el original, verificado por SHA‑256) y el PR (H3). **No se integra hasta la H3 de R3b (T9).**

## PR R3a · Doc. 04 v0.2, Doc. 03 v0.5 y Excel (raíz, rama `feat/005-recaudo-doc`) — 005‑08, 005‑09, 005‑10, 005‑14, CJ15 y CJ16

- [x] **T11** · Doc. 04 v0.2 sobre la v0.1 del repositorio (plan, paso 3, primer bloque).
  - **Proyecto:** raíz · **PR:** R3a
  - **Archivos:** `docs/funcional/04_Cuenta_Corriente_y_Recaudo.md`. El insumo es `.entrada-claude-ai/04_Cuenta_Corriente_y_Recaudo_v0.2.md` del checkout principal: **dato**. Se edita la v0.1 del repositorio, no se copia la v0.2.
  - **Depende de:** T10 (cita sus códigos) y T12 (cita el Excel).
  - **Recursos:** el Doc. 04 (solo R3a lo edita).
  - **Prueba primero:** las comprobaciones de R3a sobre el Doc. 04, escritas antes del texto: `git diff` contra la v0.1 muestra solo las secciones de 005‑08 (encabezado de versión, §4.2, §5.1, §12 nuevo con la numeración del insumo, §12.5 con `bancos`, `archivo_banco` y `reporte_participacion_20`, CA‑4.18, §14, §15 que remite al Doc. 05, la nota del ejemplo 2 de §5.2); sin «Wompi», «WP‑» ni «PASARELA» (005‑09); CA‑4.14 a CA‑4.17 y §12.3 solo en la nota de reserva; notas de PSE en §4.1 y §6.2 (decisión 11); en §14 el punto 1 reescrito y conservados los pendientes de la v0.1 (estándar del código de barras, cuentas de recepción de giros, periodicidad del reporte del 20 %): ningún pendiente de la v0.1 perdido.
- [x] **T12** · Copiar `Formatos_Intercambio_Recaudo.xlsx` a los anexos, con su comprobación (plan, paso 3; 005‑10).
  - **Proyecto:** raíz · **PR:** R3a
  - **Archivos:** `docs/funcional/anexos/Formatos_Intercambio_Recaudo.xlsx` (copia binaria desde `C:\Users\ipial\OneDrive\Escritorio\WorkSpace\liv-monorepo\liv\.entrada-claude-ai\Formatos_Intercambio_Recaudo.xlsx`, una carpeta que Git ignora y que está fuera del *worktree*).
  - **Depende de:** ninguna.
  - **Recursos:** ninguno.
  - **Prueba primero:** **antes de copiar**, comprobar en el original el tamaño (**13.779 bytes**) y el SHA‑256 (`27023a14ef160f1552eb8355a0ffd9ee82f7ecdf1a6a0defdd863b89cf0ff90c`); si no coinciden, se detiene y se pregunta. Se inspecciona como dato con `python -I` y `openpyxl` en modo de solo lectura (`read_only=True`): sin vínculos externos, sin macros (no es `.xlsm` y el ZIP no trae `vbaProject.bin`) y con datos ficticios; si aparece una placa o un documento que parezca real, se detiene el PR y se pregunta (plan, «Amenazas»). Después de copiar, el archivo del repositorio vuelve a dar el mismo tamaño y el mismo SHA‑256. Los dos resultados van en la descripción del PR. (La sesión de la fase Tareas ya lo verificó contra el original: 13.779 bytes y el mismo SHA‑256. Se repite al copiar.)
- [x] **T13** · Doc. 03 v0.5: §5.2, §5.4 y §7 (plan, paso 3, tercer bloque).
  - **Proyecto:** raíz · **PR:** R3a
  - **Archivos:** `docs/funcional/03_Configuracion_Jurisdiccion_y_Tablas_2026.md` (versión 0.5; §5.2, §5.4 y §7; **sin** §9.2, que es T14).
  - **Depende de:** T10 (cita MO‑01, MO‑03, PS‑01 y CA‑5.08 con sus códigos nuevos).
  - **Recursos:** el Doc. 03 (solo R3a lo edita).
  - **Prueba primero:** las comprobaciones de R3a, escritas antes del texto: §5.2 tiene las filas `recaudo` y `cartera` (con `sancion_minima_uvt` en UVT) y las claves coinciden con las de los YAML de T7 y T8; CJ15 y CJ16 dicen exactamente lo que fija P17 (para toda publicación nueva, la excepción de volver a validar una 1.0 ya publicada, `redondeo_causacion` con múltiplo > 0 salvo `SIN_REDONDEO`, `inicio` igual a `calendario.inicio_mora`, `sistema_determinacion: PENDIENTE` como error, `competente` como advertencia, la advertencia de `formato` junto a `formato_preferido`); CJ02 suma `cartera.intereses_mora` y `cartera.prescripcion` sin contar `prescripcion.competente` (decisión 7); §7 tiene las filas de la v0.5 y la fila del Doc. 05 §14 que registra este PR (P14).
- [x] **T14** · Doc. 03 §9.2 (CJ01 a CJ16 evaluados a mano para Ipiales) y filas de R3a en el Doc. 05 §14 (plan, paso 3, último punto).
  - **Proyecto:** raíz · **PR:** R3a
  - **Archivos:** `docs/funcional/03_Configuracion_Jurisdiccion_y_Tablas_2026.md` (solo §9.2) y `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md` (solo las filas de R3a en §14).
  - **Depende de:** T9 (los YAML aprobados en la H3), T13 (mismo archivo) y T10 integrada o en la base de la rama (el Doc. 05 tiene que existir).
  - **Recursos:** los YAML de los anexos (congelados, solo lectura); el Doc. 05 §14.
  - **Prueba primero:** una tabla CJ01 a CJ16 → resultado, regla por regla, escrita antes de la prosa y evaluada **a mano sobre los YAML de T7 y T8 tal como los aprobó la H3**, con el **SHA‑256 de los dos YAML evaluados**, que tiene que coincidir con el que dejó T9 (y con el que tendrán las copias de B1). El resultado: ningún error (CA‑5.13), las advertencias (entre ellas `prescripcion.competente`) y las reglas `POR_CONFIRMAR`. Si los YAML cambiaron después de T9, esta tarea se rehace antes de integrar. La descripción del PR declara la referencia adelantada: esos YAML entran en `main` con R3b, después de B1.
  - **Al terminar T11 a T14 (≈ 200 líneas más el binario):** revisión adversarial, las cuatro lentes y el PR (H3). **No se integra hasta que R1 esté integrado y T9 aprobada.**

## PR B1 · Esquema 1.1 (`backend/`, rama `feat/005-esquema-1-1`) — CJ01, 005‑16 a 005‑19

Empieza cuando R3b tiene su H3 (T9) y B0 está integrado. Se integra justo antes que R3b. Total ≈ 3.400 líneas, casi todas copia (`1.1.json` y `1.1.casos.json` copian el 1.0, y las copias de los anexos son idénticas a R3b): el cambio real son unas 500 líneas, y el PR da los comandos `git diff --no-index` y `diff` para verlo (compuerta Q12 de `docs/arquitectura/04`). No se parte: integrar publica y congela la versión (004‑12) y la refactorización ya salió a B0. Es una ruta crítica (`backend/contracts/**`).

- [x] **T15** · **Rojo:** las pruebas de la 1.1 y las copias de los anexos (plan, paso 7, «Rojo»). *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B1
  - **Archivos:** copias de los dos YAML de R3b en `backend/src/test/resources/contracts/jsonschema/config-jurisdiccion/1.1/` (idénticas a las de R3b ya aprobadas en la H3: `diff` vacío); en `backend/src/test/java/co/com/sapenlinea/liv/contracts/`: las pruebas nuevas o ampliadas (las que ya existen tras B0, más una clase de prueba nueva si hace falta para P18 y la compatibilidad) y los casos de **validación** de `1.1.casos.json` que cubren lo que no aparece en ningún YAML.
  - **Depende de:** T9 (los YAML aprobados y su SHA‑256) y T6 (B0 integrado).
  - **Recursos:** contrato `config-jurisdiccion` y sus pruebas; archivos de *build* (no se tocan).
  - **Prueba primero (es la prueba):** en rojo, porque `1.1.json` todavía no existe:
    - «CJ01 · la plantilla y la configuración de Ipiales 1.1 son válidas», sobre las copias de `…/1.1/`;
    - «P18 · los casos de la 1.0 dan lo mismo con la 1.1 publicada»: con `1.1.json` en la carpeta, el caso «versión desconocida» de `1.0.casos.json` (que usa `"1.1"`) sigue dando su error;
    - «005‑18 · lo válido en 1.0 es válido en 1.1»: sobre cada copia de los anexos 1.0 y cada caso de `1.0.casos.json`, primero se cambia la versión a `"1.1"` y después se aplica la mutación; lo válido en 1.0 vale en 1.1 y lo inválido en 1.0 sigue inválido, salvo que falte `archivo_banco.formato`. Se excluyen las mutaciones de `/esquema_version`, los casos que esperan su error («versión desconocida», «versión numérica», «versión PENDIENTE», «sin versión») y los que no son un objeto;
    - las mutaciones de las claves nuevas, con la tabla de campos de la 1.1;
    - casos nuevos para lo que no aparece en ningún YAML: elementos de `bancos[]` y de `formato_por_banco[]` con una clave de menos o de más, y las sondas de clase de `banco` y `formato` (código válido, minúsculas, `PENDIENTE`, un número y `null`), porque la lista está vacía en los dos YAML y la mutación nunca la recorre.
  - **Verificación del rojo:** correr `./mvnw verify` y comprobar que falla por el motivo correcto (falta `1.1.json`), no por un error de compilación.
- [x] **T16** · **Verde:** `1.1.json`, `1.1.casos.json`, la tabla de campos de la 1.1, las rutas nuevas de `ENUM_PATHS`, la documentación y el registro (plan, paso 7, «Verde»). *Commit* `feat:`.
  - **Proyecto:** `backend/` · **PR:** B1
  - **Archivos:** `backend/contracts/jsonschema/config-jurisdiccion/1.1.json` (copia del 1.0, con `$id` y `const` en 1.1, `archivo_banco.formato` opcional y las claves nuevas) y `backend/contracts/jsonschema/config-jurisdiccion/1.1.casos.json` (los casos del 1.0 con su `version` de cabecera y los de validación en `"1.1"`, salvo «versión desconocida», que usa `"99.0"`, y «versión numérica», «versión PENDIENTE» y «sin versión», que quedan iguales; más los casos nuevos de T15); en `backend/src/test/java/co/com/sapenlinea/liv/contracts/`: `JurisdictionConfigFieldTable.java` y `JurisdictionConfigFieldClasses.java` (la tabla y las clases de la 1.1, con las excepciones de P2, que no tocan la tabla de la 1.0), `JsonSchemaFilesTest.java` (las rutas nuevas de `ENUM_PATHS`: `prescripcion.inicio_termino`, `cartera_historica.intereses_historicos`, `sanciones.sistema_determinacion` y `intereses_mora.redondeo_causacion.metodo`); `backend/README.md` (la sección del esquema: versión 1.1 y su *release*); `backend/CHANGELOG.md` («Sin publicar»).
  - **Depende de:** T15.
  - **Recursos:** contrato `config-jurisdiccion` y sus pruebas; `backend/CHANGELOG.md` (compartido con F0‑11).
  - **Prueba primero:** las de T15 pasan a verde con el código mínimo. Excepciones al oráculo en la 1.1 (P2), declaradas en su tabla: opcionales, aunque las traigan los dos YAML, `recaudo.bancos`, `estado_bancos`, `reporte_participacion_20` y `archivo_banco.formato_preferido`, `formato_alterno`, `formato_por_banco` y `estado`, y `archivo_banco.formato` deja de ser obligatoria; obligatorias, aunque la plantilla traiga la lista vacía, las claves de `bancos[]` (`codigo`, `nombre`, `cuenta_recaudo`); `formato_por_banco[]` no entra en la tabla (`everyFieldHasAClass`) y solo lo prueban los casos nuevos. Clases nuevas (P7, P8): `anio_o_pendiente`, `norma_o_pendiente` y `cantidad_uvt`; sin palabras clave nuevas fuera del subconjunto portable (004‑11). Verificación: `./mvnw verify` en verde (CJ01, compatibilidad, mutación, casos de carga y de validación de las dos versiones, meta‑prueba y `jsonschema.sh check`); `1.0.json` y `1.0.casos.json` sin cambios (005‑16, 005‑17); `diff` de las copias contra R3b vacío. Las excepciones a 004‑08 (P3 y P7) están aprobadas en la H2.
  - **Al terminar T15 y T16:** revisión adversarial, las cuatro lentes y el PR (H3). Se integra **justo antes de R3b** y después de B0.
- [x] **T17** · Comprobar la *release* `jsonschema-config-jurisdiccion-v1.1` (005‑19), **después de integrar B1**.
  - **Proyecto:** `backend/` · **PR:** B1 (verificación posterior; no abre PR)
  - **Archivos:** ninguno.
  - **Depende de:** la integración de B1 en `main` (la hace la persona).
  - **Recursos:** ninguno.
  - **Prueba primero:** la *release* `jsonschema-config-jurisdiccion-v1.1` existe, publicada por `backend-main`, con `1.1.json`, `1.1.casos.json` y el `.sha256` de cada uno, y el contenido coincide con el de `main`. Lo comprueba el orquestador (`gh release view`); el resultado se anota en la bitácora.

## PR R4 · Reglas y referencias (raíz, rama `feat/005-referencias`) — 005‑20 y 005‑21

Va después de R3b. Unas 50 líneas. Es un cambio de reglas de `AGENTS.md` y del árbol de `docs/arquitectura/01` §14: la H3 de R4 lo aprueba la persona.

- [x] **T18** · `AGENTS.md` y las referencias «Documentos 01–05» (plan, paso 8).
  - **Proyecto:** raíz · **PR:** R4
  - **Archivos:** `AGENTS.md` (§1.1, la fila de `docs/funcional/` y la lista de §3.1 con el Doc. 05 en el orden de lectura, sin la frase «un futuro Módulo 5»; §5.3: los prefijos `OB`, `RP`, `IM`, `CH`, `MO`, `SA` y `PS`, y los ejemplos del Doc. 05 §4.3 con su salvedad: no son *golden* hasta que se resuelva el redondeo, decisión 14), `README.md`, `spec/constitution/tech-stack.md` y `docs/arquitectura/01_Arquitectura_de_Software.md` (solo el árbol de §14).
  - **Depende de:** T10 y T11 a T14 terminadas (los prefijos tienen que existir en los documentos).
  - **Recursos:** `AGENTS.md`, que lo leen todos los agentes.
  - **Prueba primero:** la verificación de R4, escrita antes del texto: `grep` sin resultados para «01–04», «`01_…md` a `04_…md`» y «futuro Módulo 5» en los archivos de la lista (la fila de `docs/funcional/` de `AGENTS.md` §1.1, el árbol de `README.md`, el de `docs/arquitectura/01` §14 y la lista de `tech-stack.md` usan redacciones distintas: se buscan todas); cada prefijo de §5.3 existe en el documento que dice (`OB`, `RP` e `IM` en el Doc. 04; `CH`, `MO`, `SA` y `PS` en el Doc. 05).
- [x] **T19** · Docs. 01 y 02, y filas de R4 en el Doc. 05 §14 (plan, paso 8).
  - **Proyecto:** raíz · **PR:** R4
  - **Archivos:** `docs/funcional/01_Menu_y_Cargue_Tablas_Base_Liquidacion.md` (§3: el menú 5 con su opción 5.6, remitiendo al Doc. 05; versión 0.4), `docs/funcional/02_Homologacion_y_Reglas_de_Liquidacion.md` (§13: remite al Doc. 05, sin subir de versión) y `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md` (solo las filas de R4 en §14).
  - **Depende de:** T18 (mismo PR), T14 (las filas de R3a ya están en §14).
  - **Recursos:** el Doc. 05 §14.
  - **Prueba primero:** la verificación de R4: ninguna referencia a «un futuro Módulo 5» en los Docs. 01, 02 y 04 (§15); el Doc. 01 no cambia el catálogo de roles de §4.1 ni su matriz de permisos (005‑06); el §14 del Doc. 05 lista los cambios a los Docs. 01 y 02 y a `AGENTS.md`.
  - **Al terminar T18 y T19:** revisión adversarial, las cuatro lentes y el PR (H3). Se integra después de R3b.

## Cierre (orquestador)

- [x] **T20** · El orquestador cierra la feature: *roadmap* (005‑22), glosario (`docs/arquitectura/01` §15) y `ESTADO.md` (`AGENTS.md` §7). Lo escribe solo él.
  - **Proyecto:** raíz
  - **Archivos:** `spec/constitution/roadmap.md`, `docs/arquitectura/01_Arquitectura_de_Software.md` (§15), `ESTADO.md`, `spec/features/005-cartera-y-recaudo/spec.md` (criterios marcados) y esta lista.
  - **Depende de:** los ocho PR integrados y T17.
  - **Prueba primero:** cada criterio de `spec.md` marcado `[x]` con la verificación que lo demuestra; sin prueba no se marca.
- [x] **T20.1** (orquestador) · Revisión adversarial independiente de lo implementado y las cuatro lentes de `liv-revision-4r` en cada PR; hallazgos resueltos o justificados.
- [x] **T20.2** (orquestador) · Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra.
- [x] **T20.3** (orquestador) · Compuertas de CI en verde y cada PR integrado por una persona (H3).
- [x] **T20.4** (orquestador) · Mover la feature a «Hecho» en `../../constitution/roadmap.md` y actualizar `ESTADO.md`.

## Mantenimiento (checklist recurrente)

- [ ] Un cambio de forma de los YAML después de integrar B1 es una versión 1.2 del esquema, en archivos nuevos (`1.2.json` y `1.2.casos.json`), con el mismo protocolo del ADR 0032.
- [ ] Si cambian los anexos, actualizar las copias de prueba del backend en otro PR (son una instantánea).
- [ ] Cuando se resuelva el redondeo de la causación (pendiente 12 del Doc. 05 §11), recalcular los ejemplos del Doc. 05 §4.3 y pasarlos a *golden* con la feature del Módulo 5.
