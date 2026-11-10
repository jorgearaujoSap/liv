# 005 · Cartera y recaudo: documentación funcional, configuración y esquema 1.1 — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** aprobado (H2) — 2026-11-09, por la persona que dirige el trabajo (`AGENTS.md` §4.3), sobre el *commit* `33dd948`, con P19 en la opción (a). Ver «Revisión»
_(borrador → aprobado (H2))_

## Enfoque

La feature lleva al repositorio documentación funcional, configuración y un contrato, sin código de producción. Se reparte en **ocho PR de un solo proyecto** (`AGENTS.md` §4.6): seis de la raíz (R0, R1, R2, R3a, R3b y R4) y dos del backend (B0 y B1).

- **Los documentos se incorporan desde el insumo** de `.entrada-claude-ai/`, como dato, con las 14 decisiones de la H1 y las decisiones P1 a P19 de este plan ya aplicadas. Del Doc. 04 solo cambian las secciones que lista 005‑08: se edita la v0.1 del repositorio, no se copia la v0.2.
- **Los dos YAML se escriben una vez**, en el PR de los anexos (R3b). El PR del esquema (B1) los copia ya aprobados a sus recursos de prueba, como instantánea (IN‑01).
- **El esquema 1.1 es el 1.0 más lo nuevo:** `1.1.json` parte de una copia de `1.0.json`, así que lo que se revisa es `git diff --no-index 1.0.json 1.1.json`. El 1.0 no cambia (005‑16), y el mecanismo es el de la feature 004 (ADR 0032).
- **Orden:** primero los documentos que respaldan el contrato, después el contrato y al final los anexos que lo declaran (P1). Ningún PR deja en `main` una referencia a algo que todavía no existe, con dos excepciones declaradas, que se cierran en la misma feature y en un orden fijo:
  - R1 y R3a se citan entre sí (los códigos del Doc. 05 y las reglas CJ15 y CJ16): esperan los dos la H3 de R3b, se integran seguidos y cada uno lo dice en su descripción;
  - el Doc. 03 §9.2 de R3a cita el SHA‑256 de los YAML de R3b, que entran en `main` después de B1: R3a lo dice en su descripción, y la referencia se cierra al integrar R3b.

## Implementación

Cada paso es un PR, que solo toca sus archivos (los detalla `tasks.md`). El orden de integración, que es obligatorio, está en «Dependencias y recursos», «Orden entre proyectos».

1. **R2 · ERD 2.2** (raíz, `feat/005-erd`) — 005‑15.
   - **Encabezado 2.2:** de 65 a 79 tablas, y por qué.
   - **`ledger`, de 7 a 19 tablas** (Doc. 04 §11, los nueve puntos):
     - **Tablas nuevas:** `payment_receipt`, `payment_receipt_line`, `bank_file`, `bank_file_row`, `bank_reconciliation`, `distribution_batch`, `participation_report`, `participation_report_row`, `participation_match`, `participation_claim`, `credit_balance_use` y el catálogo `movement_type` (P11), con su signo y su componente permitido.
     - **Restricciones:**
       - signo por tipo (punto 8):
         - el catálogo tiene UNIQUE (`code`, `sign`), y `sign` vale `+`, `-` o `±`. Llevan `±` en el catálogo los dos tipos sin signo fijo, `ASSESSMENT_ADJUSTMENT` y `REVERSAL` (que en el Doc. 04 §2.1 figuran como ± y ∓);
         - `tax_account_movement` guarda `movement_sign`, con una FK compuesta (`movement_type`, `movement_sign`) al catálogo, y un CHECK local: con `+`, `signed_amount > 0`; con `-`, `< 0`; con `±`, distinto de 0. Un CHECK no puede consultar otra tabla;
         - en la transacción que registra el movimiento se validan el componente permitido y, en un `REVERSAL`, que su monto sea el opuesto exacto del de su `reversal_of_id` y que vaya al mismo componente. Esto no se puede expresar con una FK ni con un CHECK;
       - `payment.idempotency_key` es UNIQUE (punto 4);
       - `tax_obligation.assessment_id` admite nulo solo con `origin = MIGRACION`, mediante un CHECK, porque las obligaciones migradas no tienen liquidación. Si otra feature crea un origen nuevo sin liquidación (por ejemplo, la declaración), amplía el CHECK.
     - **Columnas nuevas en `tax_obligation`:** `origin_modality`, `declared_address_municipality_id`, `discount_deadline`, `origin` (nueva, `LIQUIDACION` o `MIGRACION`), `final_assessment_date` (fecha de ejecutoria de la factura) y `jurisdiction_config_id`, la configuración que rige la obligación: la de su liquidación y, en las migradas, la que decida el pendiente 14 (P13). La exigibilidad y la fecha de prescripción no se guardan (P13).
     - **Columnas nuevas en otras tablas:**
       - `payment`: `receipt_id`, `channel` y `bank_file_row_id`;
       - `revenue_distribution`: `distribution_batch_id`, `component_type` y `days_to_transfer`;
       - `credit_balance`: `origin_type` y `origin_payment_id`. Además, `available_amount` pasa a `original_amount`: lo disponible se calcula restando de él la suma de los usos, y cada uso se registra bloqueando la fila del saldo (`SELECT … FOR UPDATE`) para que dos usos simultáneos no superen el monto (P16).
     - **`v_obligation_balance`** pasa a ser una vista normal con `security_invoker`, no materializada, y nunca es la fuente de una imputación (P16). Aísla porque las tablas de `ledger` que lee tienen `administration_id` y RLS (P19, opción (a)).
   - **`portfolio`, de 11 a 13 tablas** (Doc. 05 §10, sobre las tablas que ya existen; decisión 2):
     - **`interest_rate`:** `administration_id` nulo para la tasa nacional (P10), `usury_rate`, `support_pdf_sha256`, `status`, `uploaded_by` y `approved_by`, con CHECK `approved_by <> uploaded_by`. La resolución de la SFC es el `legal_instrument_id` que ya existe. Se quitan `annual_effective_rate`, que la usura menos los puntos de la configuración deriva, y la idea de guardar los puntos restados, que ya están en `cartera.intereses_mora.puntos_a_restar`: la tasa aplicada queda en el detalle de cada cálculo. El período usa las `valid_from` y `valid_to` que ya existen, un mes calendario, con llave única parcial por tipo y `valid_from` solo para la tasa `APROBADA`, para que una tasa en borrador rechazada se pueda volver a cargar.
     - **`interest_calculation`:** `reason`, `accrual_from`, `accrual_to`, `total_unrounded`, `total_rounded`, `account_movement_id` y `reversed_by_movement_id`, como el `reversed_by` del insumo. Esta última apunta al movimiento `REVERSAL` que anula su `INTEREST_ACCRUAL`, no al cálculo nuevo, y se llena una sola vez, como `superseded_by` de la configuración (Doc. 03 §5.6). La idempotencia (Doc. 05 §2.6) es un índice único parcial por obligación, `accrual_to` y `rule_version`, solo para los cálculos vigentes que no son simulación (`WHERE reversed_by_movement_id IS NULL AND reason <> 'SIMULACION'`). Un recálculo después de un reverso (MO‑05) se escribe en una sola transacción y en este orden:
       1. el movimiento `REVERSAL` del `INTEREST_ACCRUAL` viejo;
       2. `reversed_by_movement_id` del cálculo viejo, que así sale del índice;
       3. el cálculo nuevo.

       Como el viejo sale del índice antes de insertar el nuevo, no hace falta diferir nada, y un índice único parcial no se puede diferir. Una simulación nunca entra en el índice ni tiene movimiento. Se quitan `interest_rate_id` y `applied_annual_rate` de la cabecera, porque con varios tramos van en el detalle.
     - **`interest_calculation_detail`:** `interest_rate_id`.
     - **`prescription_event`:** `annuls_event_id`, `support_document_id` y `act_number`.
     - **`prescription_evaluation`:** `origin`, `case_status`, `act_number`, `act_date`, `decision` y `write_off_movement_id`.
     - **`collection_portfolio_snapshot`:** `cutoff_id`, `administration_id`, `taxpayer_id`, `vehicle_id`, `fiscal_year`, `prescription_deadline`, `traffic_light` y `origin_modality`, particionada por fecha. Es una foto inmutable, no un saldo vivo.
     - **`opening_balance_row`:** los campos de la plantilla del Doc. 05 §3.1 que no tiene: `invoice_number`, `notification_date`, `final_date`, `penalty_act_number`, `penalty_act_final_date`, `legacy_interest` y `support_reference`.
     - **Tablas nuevas:** `portfolio_cutoff` (P12), con CHECK `published_by <> generated_by`, y `opening_balance_event`, la hoja de eventos de cobro de la plantilla (§3.1), en *staging*.
   - **`tax.penalty_rule`:** `rule_code` (reemplaza a `penalty_type`), `active`, `applies_if`, `tax_cap_rate` (reemplaza a `maximum_amount`), `minimum_uvt` (reemplaza a `minimum_amount`) y `status`, para que la publicación pueda materializar `cartera.sanciones` (P9). La sanción mínima se guarda en UVT, no en pesos: el valor en pesos depende del año.
   - **Aislamiento** (`AGENTS.md` §5.1; hallazgo 2 del ERD): todas las tablas nuevas llevan `administration_id` y FK compuestas con su padre. Las cuatro tablas de `ledger` que hoy no lo tienen (`obligation_component`, `tax_account_movement`, `payment_allocation` y `revenue_distribution`) lo reciben también, con FK compuesta hacia su padre (`obligation_id` o `payment_allocation_id` con `administration_id`) y su política RLS (P19, opción (a)). Así todo `ledger` queda con `administration_id`. Hay dos excepciones:
     - `ledger.movement_type`, un catálogo fijo que escriben las migraciones;
     - `portfolio.interest_rate`, con `administration_id` nulo para la tasa nacional (`docs/arquitectura/02` §5, que dice que los datos nacionales solo los escribe el proceso de publicación; P10).

     Con la opción (a), las FK de `revenue_distribution.distribution_batch_id` y de `credit_balance_use` hacia `tax_account_movement` pasan a ser compuestas. Queda una sola FK nueva que no puede serlo, `interest_calculation.account_movement_id`, porque `portfolio.interest_calculation` no tiene `administration_id`: se valida en la transacción, como admite el hallazgo 2 del ERD, y el ERD lo dice en ella. Las tablas de `portfolio` que ya existían no se tocan en este punto.

     Los archivos (`bank_file` y `participation_report`) tienen UNIQUE por administración y SHA‑256 (CA‑4.05). **Segregación:** `bank_file`, `bank_reconciliation`, `distribution_batch`, `participation_report`, `interest_rate` y `portfolio_cutoff` llevan quien carga o genera y quien aprueba o publica, con un CHECK de que son personas distintas (Doc. 04 §11, punto 3, el mismo patrón del Módulo 1; Doc. 05 §2.5).
   - Tamaño: unas 380 líneas.

2. **R1 · Doc. 05 v0.1** (raíz, `feat/005-doc05`) — 005‑01 a 005‑07. Se integra después de R2, porque cita sus tablas, y después de la H3 de R3b, para entrar justo antes que R3a (P1). Crea `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md` desde el insumo, con estos ajustes:
   - **Códigos** (decisión 1): `CH‑01`…`CH‑10`, `MO‑01`…`MO‑10`, `PS‑01`…`PS‑04` y `SA‑01`…`SA‑05`, también en las referencias cruzadas.
   - **Tablas y campos en inglés**, con los nombres del ERD 2.2 de R2 (decisión 2, P10 a P13).
   - **Roles:** donde aparezca un rol propuesto (§3.4, §9, PS‑02), se marca como propuesta (decisión 3).
   - **§0:** se titula «Marco normativo» y lleva una nota: está pendiente de la validación de jurídica y de la fuente oficial (la UVT, las tasas y la fórmula). La UVT y las tasas de §4.1 llevan «por verificar» (decisión 6).
   - **§4.3:** CA‑5.03 aclara que sus valores son los de un cálculo único, sin cortes intermedios (decisión 4). Se quita la referencia a `scripts/intereses_mora.py`.
   - **§5.1:** las reglas se leen de la configuración y se materializan en `tax.penalty_rule` al publicarla (P9).
   - **§6.3:** los ejemplos quedan como ilustrativos (decisión 5), con dos aclaraciones: usan `FECHA_LIMITE_PAGO`, e Ipiales tiene configurada `FECHA_EJECUTORIA_FACTURA`.
   - **§11:** 19 pendientes, cada uno con su responsable: los 8 del insumo, sin cambios, y los 11 nuevos de la tabla «Pendientes del Doc. 05 §11».
   - **§12:** CA‑5.13 dice a qué publicaciones se aplican CJ15 y CJ16 (P17).
   - **§14 nuevo, «Cambios aplicados a otros documentos»** (P14). Lista solo cambios que ya están en `main` o que llegan en un PR que se integra antes: el ERD (R2). Los de R3a y R4 los registran esos PR al agregar sus filas.
   - **Referencia adelantada:** CA‑5.13 cita CJ15 y CJ16, que define R3a. R1 y R3a esperan la H3 de R3b y se integran seguidos, y los dos PR lo declaran (Enfoque).
   - **Tamaño:** unas 480 líneas, todas de un archivo nuevo. Se justifica en el PR (Q12): es un solo documento, y partirlo dejaría uno a medias en `main`.

3. **R3a · Doc. 04 v0.2 y Doc. 03 v0.5** (raíz, `feat/005-recaudo-doc`) — 005‑08, 005‑09, 005‑10, 005‑14, CJ15 y CJ16. Se integra después de R1, porque cita sus códigos, y **después de la H3 de R3b**, porque describe y evalúa sus YAML (P1).
   - **`04_Cuenta_Corriente_y_Recaudo.md`**, sobre la v0.1:
     - las secciones de 005‑08;
     - §12, con la nota de la pasarela en su introducción: §12.3 y CA‑4.14 a CA‑4.17 quedan reservados para ella (decisión 13). Las demás secciones conservan la numeración del insumo: §12.1 sin la fila de la pasarela, §12.2, que remite al anexo, §12.4 y §12.5;
     - las notas de PSE en §4.1 y §6.2 (decisión 11);
     - la nota del ejemplo 2 de §5.2 (parte B, punto 7);
     - §15, que remite al Doc. 05;
     - en §14, el punto 1 se reescribe (bancos resuelto, pasarela después del piloto), y se conservan como pendientes, de la v0.1, el estándar del código de barras, las cuentas de recepción de giros y la periodicidad del reporte del 20 %. No se pierde ningún pendiente.
   - **`docs/funcional/anexos/Formatos_Intercambio_Recaudo.xlsx`:** copia binaria del insumo, en este PR porque el Doc. 04 la cita y no depende del esquema. Antes de subirla se comprueban sus 13.779 bytes y su SHA‑256 (`27023a14…0ff90c`), y se inspecciona con `openpyxl` como dato, en modo de solo lectura: sin vínculos externos ni macros, y con datos ficticios (`AGENTS.md` §4.5).
   - **`03_Configuracion_Jurisdiccion_y_Tablas_2026.md`**, a la v0.5:
     - **§5.2:** las filas `recaudo` y `cartera`. En `cartera`, la unidad de `sancion_minima_uvt` es la UVT.
     - **§5.4:** CJ15 y CJ16, con los ajustes de P17. CJ02 suma a sus reglas obligatorias `cartera.intereses_mora` y `cartera.prescripcion`, sin contar `prescripcion.competente` (decisión 7).
     - **§7:** las filas de la v0.5, y la fila del Doc. 05 §14 que registra este PR (P14).
     - **§9.2:** el resultado de Ipiales con CJ01 a CJ16, evaluado a mano regla por regla sobre los YAML de R3b ya aprobados en su H3. El PR lo muestra en una tabla, con el SHA‑256 de los YAML evaluados, que tiene que coincidir con los de R3b al integrar. Es una referencia adelantada declarada: esos YAML entran en `main` con R3b, después de B1 (Enfoque).
   - Tamaño: unas 200 líneas, más el binario.

4. **R0 · ADR 0034, «Casos comunes por versión»** (raíz, `feat/005-adr-0034`), con `liv-adr`, antes de B0 (paso 5). Un ADR aceptado no se edita (`liv-adr`), así que la regla va en un ADR nuevo que **complementa** al 0032 sin reemplazarlo: los casos de la versión X se evalúan con las versiones publicadas hasta X, también en el validador de la 1.5.9. La H2 aprueba la decisión (P18). R0 escribe el ADR como «Propuesto», y la persona lo acepta formalmente en la H3 de R0, y el PR lo deja «Aceptado» antes de integrarse. La tabla del doc. 00 §6 lo cita. El ADR 0032 no se edita. Unas 60 líneas.

5. **B0 · Pruebas de esquema por versión** (`backend/`, `feat/005-pruebas-por-version`) — prepara 005‑16 a 005‑18, sin esquema nuevo. Es una refactorización: no cambia ningún comportamiento, y las pruebas de la 1.0 siguen iguales y en verde.
   - **Copias de los anexos:** las de la 1.0 se mueven con `git mv` a `src/test/resources/contracts/jsonschema/config-jurisdiccion/1.0/`, y `TestConfigurations` recibe la versión.
   - **`JurisdictionConfigFieldTable`, `JurisdictionConfigFieldClasses` y el oráculo de `JurisdictionConfigSchemaMutationTest`** pasan a estar por versión, con la tabla de la 1.0 tal como está. `CLOSED_LISTS` pasa de `Map.of` a `Map.ofEntries`, porque la 1.1 pasa de 10 entradas, y `closedListsAreExact` lee el esquema de cada versión.
   - **`JsonSchemaFilesTest`:** `ENUM_PATHS` por versión, con las rutas de la 1.0 sin cambio.
   - **Otras pruebas que cambian de firma, sin cambiar lo que comprueban:**
     - `JurisdictionConfigSchemaTest`, que usa `TestConfigurations.load`;
     - `SafeYamlLoaderTest` y `JurisdictionConfigCommonCasesTest`, que hoy tienen fijo `"1.0"`: pasan a recorrer cada archivo de casos, con sus casos de carga y de validación;
     - `CommonCases.load`, que recibe la versión del archivo de casos;
     - `JurisdictionConfigSchemas.forVersion`, donde se implementa P18: recibe la versión de los casos y solo resuelve las versiones publicadas hasta ella.
   - **B0 no puede demostrar P18,** porque todavía no existe la 1.1: con una sola versión, el registro limitado y el completo dan lo mismo. Lo demuestra B1 (paso 7).
   - **Los casos comunes de cada versión** se corren con el registro de versiones hasta esa versión (P18). Así el caso «versión desconocida» de `1.0.casos.json`, que usa `"1.1"`, sigue dando su error aunque exista la 1.1. Antes de abrir B0, el ADR 0034 (R0) tiene que estar integrado.
   - Tamaño: unas 300 líneas, en su mayoría movimientos.

6. **R3b · Anexos** (raíz, `feat/005-anexos`) — 005‑11 a 005‑13. Se escribe primero, se revisa en paralelo con R3a, y **se integra después de B1** (P1).
   - **Los dos YAML:**
     - `esquema_version: "1.1"`;
     - las cuatro normas; la descripción de `ET_ART_634_635` no da por hecha la fórmula;
     - `recaudo`, con P4 y P6, y sin `archivo_banco.formato`, que se reemplaza por `formato_preferido`, `formato_alterno` y `formato_por_banco` (P4 y P17);
     - `cartera`, con P3, P5, P7 y P8;
     - los estados de la decisión 6, y el comentario de `prescripcion.reinicio`: `POR_CONFIRMAR`, porque tras la reorganización o la liquidación forzosa el término corre desde que termina el proceso (pendiente 18).
     - La plantilla lleva los valores de la tabla «Valores de la plantilla».
   - **Antes de que B1 copie los YAML,** R3b pasa todas sus revisiones (las de la tabla «Verificaciones por PR», la adversarial y las cuatro lentes) y la **persona aprueba su H3**. Después no cambia. Si hace falta cambiar su forma, se cambia antes de integrar B1. Una vez integrado B1, cualquier cambio de forma es una versión 1.2.
   - Unas 230 líneas.

7. **B1 · Esquema 1.1** (`backend/`, `feat/005-esquema-1-1`) — CJ01, 005‑16 a 005‑19. Empieza cuando R3b tiene su H3 aprobada, y se integra justo antes que R3b.
   - **Rojo** (*commit* `test:`):
     - «CJ01 · la plantilla y la configuración de Ipiales 1.1 son válidas», sobre las copias de `…/1.1/`, idénticas a las de R3b;
     - «P18 · los casos de la 1.0 dan lo mismo con la 1.1 publicada»: con `1.1.json` en la carpeta, el caso «versión desconocida» de `1.0.casos.json` sigue dando su error;
     - «005‑18 · lo válido en 1.0 es válido en 1.1»: sobre cada copia de los anexos 1.0 y cada caso de `1.0.casos.json`, primero se cambia la versión a `"1.1"` y después se aplica la mutación. Lo válido en 1.0 vale en 1.1, y lo inválido en 1.0 sigue inválido en 1.1, salvo que falte `archivo_banco.formato`. Se excluyen las mutaciones de `/esquema_version` y los casos que esperan su error («versión desconocida», «versión numérica», «versión PENDIENTE», «sin versión»), y los que no son un objeto;
     - las mutaciones de las claves nuevas, con la tabla de campos de la 1.1;
     - casos nuevos en `1.1.casos.json` para lo que no aparece en ningún YAML: elementos de `bancos[]` y de `formato_por_banco[]` con una clave de menos o de más, y las sondas de clase de `banco` y `formato` (código válido, minúsculas, `PENDIENTE`, un número y `null`), porque la lista está vacía en los dos YAML y la mutación nunca la recorre.
   - **Verde** (*commit* `feat:`):
     - `1.1.json`: copia del 1.0, con `$id` y `const` en 1.1, `archivo_banco.formato` opcional y las claves nuevas;
     - `1.1.casos.json`: los casos del 1.0, con su `version` de cabecera en `"1.1"` y `"1.1"` en los de validación. Se exceptúan los casos de la versión: «versión desconocida» usa `"99.0"`, y «versión numérica», «versión PENDIENTE» y «sin versión» quedan iguales. Más los casos nuevos;
     - las rutas nuevas de `ENUM_PATHS` y la tabla de la 1.1;
     - una entrada en `CHANGELOG.md`.
   - **Excepciones al oráculo en la 1.1** (P2), que se declaran en la tabla de la 1.1 y no tocan la de la 1.0:
     - son opcionales, aunque las traigan los dos YAML, las claves nuevas de objetos que ya existían: `recaudo.bancos`, `estado_bancos` y `reporte_participacion_20`, y `recaudo.archivo_banco.formato_preferido`, `formato_alterno`, `formato_por_banco` y `estado`; además, `archivo_banco.formato` deja de ser obligatoria;
     - son obligatorias, aunque la plantilla traiga la lista vacía, las claves de `bancos[]` (`codigo`, `nombre` y `cuenta_recaudo`);
     - `formato_por_banco[]` no entra en la tabla de la 1.1 (`everyFieldHasAClass` exige que cada clase tenga un campo en algún YAML): sus claves (`banco` y `formato`, obligatorias) y sus clases solo las prueban los casos nuevos.
   - **Tamaño:** unas 3.400 líneas, casi todas copia: `1.1.json` y `1.1.casos.json` copian el 1.0, y las copias de los anexos son idénticas a R3b. El cambio real son unas 500 líneas, y el PR da los comandos `git diff --no-index` y `diff` para verlo (Q12). No se puede partir: integrar publica y congela la versión (004‑12), y la refactorización ya salió a B0.
   - **Al integrar,** `backend-main` publica la *release* `jsonschema-config-jurisdiccion-v1.1` (005‑19).

8. **R4 · Reglas y referencias** (raíz, `feat/005-referencias`) — 005‑20 y 005‑21. Va después de R3b.
   - **`AGENTS.md`:**
     - §1.1 y §3.1: el Doc. 05 en el orden de lectura;
     - §5.3: los prefijos, y los ejemplos del Doc. 05 §4.3 con su salvedad: no son *golden* hasta que se resuelva el redondeo (decisión 14).
   - **«Documentos 01–05»** en `README.md`, `spec/constitution/tech-stack.md` y el árbol de `docs/arquitectura/01` §14.
   - **Docs. 01 y 02:**
     - Doc. 01 §3: el menú 5 con su opción 5.6, remitiendo al Doc. 05. El Doc. 01 sube a la v0.4;
     - Doc. 02 §13: remite al Doc. 05, sin subir de versión.
   - **Doc. 05 §14:** las filas de estos cambios.
   - Unas 50 líneas.

9. **Cierre (orquestador):** el *roadmap* (005‑22), el glosario y `ESTADO.md` (`AGENTS.md` §7).

### Valores de la plantilla en las claves nuevas

El `LEEME.md` del insumo dice que la plantilla del proyecto tiene los mismos valores de Ipiales salvo cinco. Se sigue así, con tres desviaciones que se aprueban en la H2 y van marcadas: `estado_bancos` y `remitente`, que en Ipiales son propios de Nariño, y `sistema_determinacion` (decisión 9). Los valores de Ipiales que la plantilla repite son propuestas con su estado (`intereses_mora` en `POR_CONFIRMAR`, `cartera_historica` en `PENDIENTE`), igual que el `redondeo` de la plantilla actual repite el $1.000 con `estado: PENDIENTE`:

| Clave | Plantilla | Ipiales |
|---|---|---|
| `recaudo.canales` | `[BANCO_CONVENIO, VENTANILLA]` | igual |
| `recaudo.bancos` | `[]` (LEEME) | los cuatro bancos, con `cuenta_recaudo: PENDIENTE` |
| `recaudo.estado_bancos` | `PENDIENTE` (**desviación**) | `VIGENTE` |
| `recaudo.archivo_banco.formato_preferido` / `formato_alterno` / `formato_por_banco` / `estado` | `ASOBANCARIA_2001` / `CANONICO_CSV_V1` / `[]` / `PENDIENTE` | igual |
| `recaudo.imputacion.sin_indicacion` | `MAS_ANTIGUA_PRIMERO`, aprobada (LEEME) | igual |
| `recaudo.reporte_participacion_20` | `formato_propuesto: PARTICIPACION_CSV_V1`, `remitente: PENDIENTE` (texto libre, P8; **desviación**), `periodicidad: MENSUAL`, `estado: PENDIENTE` | `remitente: "Gobernación de Nariño o bancos"`, igual en lo demás |
| `cartera.intereses_mora` | igual que Ipiales (LEEME): las reglas del ET y, como propuesta, el redondeo a $1.000 (`MULTIPLO_MAS_CERCANO`, 1000, `HACIA_ARRIBA`), con `estado: POR_CONFIRMAR` | — |
| `cartera.sanciones.sistema_determinacion` | `PENDIENTE` (decisión 9; **desviación**) | `LIQUIDACION_FACTURA` |
| `cartera.sanciones.reglas[EXTEMPORANEIDAD_DECLARACION]` | `activo: false`, con el comentario «activar si la jurisdicción usa declaración» (LEEME) | `activo: false` |
| `cartera.sanciones.estatuto_municipal` | `PENDIENTE` (LEEME) | `PENDIENTE` |
| `cartera.sanciones.uvt`, `sancion_minima_uvt` y `requiere_acto_en_firme` | igual que Ipiales (nacionales) | — |
| `cartera.prescripcion` | igual que Ipiales, con `competente: PENDIENTE` | — |
| `cartera.cartera_historica` | `vigencias_desde: PENDIENTE`, `fecha_corte_migracion: PENDIENTE`, `intereses_historicos: RECALCULAR`, `tolerancia_conciliacion_pesos: 1000` (propuesta del insumo), `estado: PENDIENTE` | igual |
| `cartera.antiguedad` | igual que Ipiales | — |

### Pendientes del Doc. 05 §11

Son 19. Los 1 a 8 son los del insumo (Doc. 05 §11), que se conservan sin cambios y no se repiten aquí; los 9 a 19 son nuevos. Ninguno se resuelve (005‑03).

| # | Pendiente | Responsable | Origen |
|---|---|---|---|
| 9 | UVT 2026 de $52.374 contra la resolución oficial de la DIAN | Jurídica | Parte B, punto 1 |
| 10 | Tasas de usura de enero a noviembre de 2026 contra las resoluciones de la Superfinanciera | TI, con los PDF | Parte B, punto 2 |
| 11 | Fórmula de los intereses: tasa efectiva anual entre 365 (o 366), frente a la tasa diaria equivalente que nombra el art. 635 | Jurídica | Parte B, punto 3 |
| 12 | Alcance del redondeo de la causación: cada causación o el acumulado de la obligación (con causación mensual, $55.000 frente a $54.000 en el ejemplo 2) | Hacienda | Decisión 4 |
| 13 | Cómputo del término de cinco años (día inicial y final; CA‑5.09) y exigibilidad de los ejemplos | Jurídica | Decisión 5 |
| 14 | Qué configuración rige la cartera de una vigencia anterior y de las obligaciones migradas, que no tienen liquidación (`tax_obligation.jurisdiction_config_id`, P13) | Hacienda y TI | Parte B, punto 5 |
| 15 | Qué se ve del menú 5 según las modalidades activas | Hacienda | Parte B, punto 5 |
| 16 | Roles del Módulo 5 (la matriz del §9 es una propuesta) | Hacienda y TI | Decisión 3 |
| 17 | Quién carga y aprueba las tasas nacionales en un producto multi‑inquilino (`docs/arquitectura/02` §5) | TI | P10 |
| 18 | Reinicio del término tras la reorganización o la liquidación forzosa (art. 818 ET: ¿desde que termina el proceso?) | Jurídica | Revisión del plan |
| 19 | Rangos de antigüedad: 360 días no es un año, y «> 5 años» empieza en el día 1.801, antes de la prescripción | Hacienda | Revisión del plan |

## Datos, API y seguridad

- **Migraciones:** ninguna. El ERD 2.2 es modelo lógico, y las tablas se crean con las features de las Fases 4 y 5.
- **Contrato:** el esquema JSON de la configuración pasa a 1.1, **compatible** con el 1.0 (decisión 10, 005‑18). El 1.0 sigue publicado y no cambia. No hay cambios en OpenAPI.
- **Permisos:** no cambia ningún rol, permiso, *realm* ni catálogo del menú (005‑06).
- **Amenazas** (`docs/arquitectura/02` §2):
  - **Contenido externo** (`AGENTS.md` §4.4, regla 7): el insumo, el Excel y las fuentes de prensa son datos. El Excel se comprueba y se inspecciona antes de subirlo (paso 3).
  - **Datos personales:** los ejemplos del Excel y de los documentos tienen que ser ficticios. Si aparece una placa o un documento que parezca real, se detiene el PR y se pregunta.
  - **Aislamiento:** cada tabla nueva del ERD lleva `administration_id`, con dos excepciones declaradas; las FK que no pueden ser compuestas se validan en la transacción, y la vista de saldos no se salta el RLS (paso 1, P19).
  - **Integridad del contrato:** la 1.1 se publica con el protocolo del ADR 0032, y la comprobación de inmutabilidad protege la 1.0 y sus casos.

## Dependencias y recursos

- **Depende de:** nada que no esté integrado. La feature 004 ya está en `main`.
- **Recursos compartidos:**
  - el contrato `config-jurisdiccion` y sus pruebas: B0 y B1, en serie;
  - los YAML de los anexos: los escribe R3b, B1 los copia y R3a los describe y los evalúa (§9.2);
  - los archivos de *build* y el `CHANGELOG.md` del backend: B0 y B1. F0‑11 puede correr a la vez en `backend/`, porque no toca los mismos archivos, salvo el `CHANGELOG.md`, que se comparte;
  - el Doc. 05: lo crea R1, y R3a y R4 solo agregan filas a su §14, que se integran después.
- **Otros archivos, cada uno de un solo PR:** el ERD es de R2; el Doc. 04 y el Doc. 03, de R3a; el ADR 0034 nuevo y su fila en la tabla del doc. 00 §6, de R0 (el ADR 0032 no cambia); `AGENTS.md`, `README.md`, `tech-stack.md`, `docs/arquitectura/01` y los Docs. 01 y 02, de R4.
- **Orden entre proyectos** (orden de integración), obligatorio:
  1. `R2`, y en paralelo `R0 → B0`;
  2. **H3 de R3b aprobada** (R3b no cambia desde ahí);
  3. `R1 → R3a`, seguidos;
  4. `B1 → R3b → R4`;
  5. el cierre del orquestador.
  - Se pueden escribir y revisar a la vez R2, R1, R3a, R3b, R0 y B0. R3b se escribe primero, porque R3a y B1 parten de sus YAML.
  - Si la H3 de R3b pide cambiar sus YAML, R3a vuelve a evaluar §9.2 antes de integrarse.

## Decisiones

- **P1 · Ocho PR, en un orden fijo** — `AGENTS.md` §4.6 pide un proyecto por PR y «primero el backend con el contrato», y la jerarquía de fuentes (`AGENTS.md` §3) pide que el contrato no se adelante a los documentos que lo respaldan.
  - R3 se parte en R3a (documentos y el Excel, antes del contrato) y R3b (anexos, después del contrato), para que `main` nunca tenga anexos que declaren una versión que no existe, ni documentos que citen un anexo que no está.
  - Integrar B1 congela la forma de los YAML. Por eso B1 y R3a esperan a que la persona apruebe la H3 de R3b, y R3b ya no cambia hasta integrarse justo después de B1. Así el Doc. 03 §5.2 y §9.2 describen y evalúan los YAML que de verdad se integran.
  - Las referencias adelantadas son las dos que declara «Enfoque»: R1 y R3a se citan entre sí y se integran seguidos, y el §9.2 de R3a cita los YAML de R3b.
  - **Descartado:**
    - un PR con los dos proyectos, que rompe §4.6;
    - juntar R1 y R3a en un PR para evitar la referencia adelantada: pasaría de unas 680 líneas;
    - integrar R3b antes que B1, porque los anexos declararían una versión inexistente;
    - integrar B1 antes de la H3 de R3b, porque un cambio pedido en esa H3 obligaría a una 1.2.
- **P2 · El 1.1 como copia del 1.0 más lo nuevo, y compatible** (decisión 10):
  - las claves nuevas dentro de objetos que ya existen son opcionales, y `archivo_banco.formato` pasa a opcional;
  - dentro de los objetos nuevos rige la regla general del 1.0, con las excepciones del oráculo del paso 7, declaradas en la tabla de la 1.1;
  - la compatibilidad se prueba con todas las mutaciones válidas de la 1.0, no solo con los dos YAML.
  - **Descartado:** un `$ref` externo al 1.0, que el subconjunto portable prohíbe.
- **P3 · `sistema_determinacion` en `cartera.sanciones`: `LIQUIDACION_FACTURA`, `DECLARACION` o `PENDIENTE`** (decisión 9).
  - Va junto a las reglas que lo usan (`aplica_si`), así que el cambio queda en la sección nueva.
  - `PENDIENTE` hace falta en la plantilla, que sirve también a departamentos que pueden liquidar por declaración. Es una **excepción a 004‑08** («`PENDIENTE` nunca en listas cerradas»), que se aprueba en la H2.
  - En CJ16, `PENDIENTE` es un **error**, no una lectura de «no declara» (P17): una jurisdicción no publica sin decirlo, y nadie apaga las sanciones de declaración sin decidirlo.
- **P4 · `archivo_banco.formato_por_banco` como lista** `[{banco, formato}]`, vacía al principio, en lugar del mapa `{}` del insumo. Un mapa con claves libres no admite `additionalProperties: false`, que la meta‑prueba exige (004‑02). Las claves de sus elementos las prueban casos propios (paso 7).
- **P5 · Se quita `cartera.intereses_mora.tabla_tasas`.** La configuración describe reglas, no dónde las guarda el sistema: con esa clave, cambiar el nombre de la tabla obligaría a una versión nueva del esquema. De dónde vienen las tasas lo dicen el Doc. 05 §4.1 y `tasa_referencia`.
- **P6 · Se mantiene `recaudo.estado_bancos`** como clave opcional con `$defs/estado`: dice que la lista de bancos está `VIGENTE` aunque `recaudo` siga `POR_CONFIRMAR`.
- **P7 · Dos clases nuevas que admiten `PENDIENTE`,** con `anyOf` y los `$defs` que ya existen, sin palabras clave nuevas (004‑11):
  - `anio_o_pendiente`: un entero ≥ 0 o `PENDIENTE`, para `vigencias_desde`;
  - `norma_o_pendiente`: un código o `PENDIENTE`, para `estatuto_municipal`, que, cuando se diligencie, será una norma de `normas` (CJ03).
  - Las dos son **excepciones a 004‑08** («`PENDIENTE` nunca en enteros ni en códigos») y se aprueban en la H2 junto con P3. Los demás `PENDIENTE` nuevos van en clases que ya los admiten: textos libres (`cuenta_recaudo`, `competente`, `remitente`) y fechas (`fecha_corte_migracion`).
- **P8 · Listas cerradas y clases:**
  - **Listas cerradas,** solo donde el YAML documenta todas las opciones (004‑05). Cada una lleva su ruta en `ENUM_PATHS`:
    - `prescripcion.inicio_termino`: `FECHA_EJECUTORIA_FACTURA` o `FECHA_LIMITE_PAGO`;
    - `cartera_historica.intereses_historicos`: `RECALCULAR` o `TOMAR_DEL_LEGADO`;
    - `sanciones.sistema_determinacion`;
    - `intereses_mora.redondeo_causacion.metodo`: la misma lista de `redondeo.metodo`, repetida, porque `ENUM_PATHS` no admite un `$ref` fuera de `$defs`. El YAML trae un comentario con sus tres opciones (`MULTIPLO_MAS_CERCANO`, `MULTIPLO_SUPERIOR` y `SIN_REDONDEO`).
  - **Códigos abiertos:** el resto (`formula`, `dias_anio`, `tramos`, `inicio`, `declaracion`, `reinicio`, `aplica_si`, los eventos, `canales`, los formatos, `formato_propuesto`, `periodicidad`) y las referencias a normas.
  - **Textos libres:** `remitente` es un texto libre que admite `PENDIENTE`, porque nombra quién envía el reporte y en la plantilla no se sabe.
  - **Valores numéricos:**
    - fracciones: `puntos_a_restar`, `tarifa_mes_o_fraccion` y `tope_sobre_impuesto`;
    - montos en pesos: `uvt.valor` y `tolerancia_conciliacion_pesos`;
    - una clase nueva `cantidad_uvt` (número ≥ 0), para `sancion_minima_uvt`, que se mide en UVT y no en pesos (`AGENTS.md` §5.2);
    - enteros: `anios`, `uvt.vigencia`, `semaforo_dias.*`, `rangos_dias[]` y `multiplo`.
- **P9 · Las reglas de sanción salen de la configuración.** Al publicarla, `cartera.sanciones` se materializa en `tax.penalty_rule`, que el ERD 2.2 amplía para eso (paso 1), como las tarifas se materializan en `tax.tax_rate` (Doc. 03 §5.2). Cómo conviven las opciones 1.5.5 a 1.5.8 del Doc. 01 §6.7 con la configuración lo decide la feature de la opción 1.5.9, como ya pasa con las tarifas.
- **P10 · La tasa de usura es un dato nacional:** `portfolio.interest_rate` lleva `administration_id` nulo, como los datos nacionales compartidos de `docs/arquitectura/02` §5, que todos leen y solo escribe el proceso de publicación. Quién la carga y quién la aprueba queda como pendiente 17.
  - **Descartado:** una copia por administración, que repite un dato nacional y puede divergir.
- **P11 · El catálogo de tipos de movimiento es `ledger.movement_type`:** el esquema `ref` del Doc. 04 §11 no existe, y el ADR 0003 pone cada módulo en un esquema.
- **P12 · El corte mensual tiene cabecera,** `portfolio.portfolio_cutoff`. Lleva la administración, la fecha, el estado (`GENERADO`, `ERROR` o `PUBLICADO`), los totales de control, el corte al que reemplaza y quién lo generó y lo publicó. El Doc. 05 §7.1 le da estado y reemplazo al corte, y eso no cabe en las filas por obligación.
- **P13 · No se guardan la exigibilidad ni la fecha de prescripción.**
  - La exigibilidad se deriva de `prescripcion.inicio_termino` y de las fechas de la obligación.
  - La fecha de prescripción se deriva de los eventos, y el ERD ya la registra en `portfolio.prescription_evaluation.calculated_deadline`.
  - Se guarda `final_assessment_date`, que es un hecho, y `jurisdiction_config_id`, la configuración que rige la obligación. La exigibilidad se deriva siempre con esa configuración y nunca con otra posterior, para que una versión nueva no mueva una fecha con efecto jurídico. En las obligaciones migradas, qué configuración es la decide el pendiente 14.
  - Es el criterio de `AGENTS.md` §5.1 para los saldos, aplicado a un dato derivado: una sola fuente de verdad.
  - Por lo mismo, `interest_rate` no guarda la tasa de mora ni los puntos restados (paso 1): se derivan de la usura y de la configuración.
  - `legally_payable_from` no cambia: es la fecha de inicio de pago (Doc. 04 §3.1), no la exigibilidad.
- **P14 · Registro de los cambios entre documentos** (`AGENTS.md` §3.1): el Doc. 05 §14 y el Doc. 03 §7. Cada PR agrega solo las filas de los cambios que integra.
- **P15 · Un ADR nuevo, el 0034** (R0, P18). No hay dependencias, módulos ni dependencias entre módulos nuevas. El ADR 0034 complementa al 0032 sin reemplazarlo, porque un ADR aceptado no se edita (`liv-adr`). La H2 aprueba su decisión; su aceptación formal va en la H3 de R0.
- **P16 · Los saldos a favor no se guardan:** `credit_balance` guarda el monto de origen, y lo disponible se calcula con `credit_balance_use`. Cada uso bloquea la fila del saldo, para que dos usos simultáneos no lo superen. `v_obligation_balance` es solo para consultas (`AGENTS.md` §5.1).
- **P17 · Ajustes a CJ15, CJ16 y CJ02**, que precisan la decisión 8 de la H1 y se aprueban en esta H2:
  - CJ15 y CJ16 se aplican a **toda publicación nueva**, declare la versión que declare: una configuración nueva en 1.0, que no tiene `cartera`, falla CJ15 si alguna modalidad activa liquida. Así nadie se salta las reglas de mora declarando `"1.0"`. La excepción es volver a validar una configuración 1.0 ya publicada, que se valida con las reglas de cuando se publicó;
  - el múltiplo de `redondeo_causacion` debe ser mayor que 0 con cualquier método salvo `SIN_REDONDEO`, no solo con `MULTIPLO_MAS_CERCANO`;
  - CJ15 comprueba que `cartera.intereses_mora.inicio` coincida con `calendario.inicio_mora`;
  - en CJ16, `sistema_determinacion: PENDIENTE` es un error (P3);
  - CJ02 suma `cartera.intereses_mora` y `cartera.prescripcion` a sus reglas obligatorias, sin contar `prescripcion.competente` (decisión 7);
  - los anexos 1.1 no traen `archivo_banco.formato`. Si una configuración trae `formato` y también `formato_preferido`, CJ16 da una advertencia y vale `formato_preferido`.
- **P18 · Cómo se corren los casos comunes:** los de la versión X se evalúan con las versiones publicadas hasta X. Así, que exista una versión nueva no cambia el resultado de un caso ya publicado e inmutable: el caso «versión desconocida» de la 1.0 usa `"1.1"`.
  - La H2 aprueba la decisión, y la registra el ADR 0034 (R0), que complementa al 0032 y que la persona acepta en la H3 de R0. Obliga también al validador de la 1.5.9.
  - **Descartado:** llamar 1.2 a la versión nueva, que cambia los criterios aprobados en la H1 y deja un hueco en la numeración, y cambiar `1.0.casos.json`, que es inmutable.

- **P19 · La vista de saldos respeta el RLS.** `v_obligation_balance` es una vista normal con `security_invoker` (PostgreSQL 15 o posterior; el proyecto usa la 18.6), no materializada. PostgreSQL no aplica RLS a las vistas materializadas: una así guardaría los saldos de todas las administraciones con el rol dueño. Si una consulta necesita rendimiento, se evalúa una vista materializada con `administration_id`, sin permiso de lectura para los roles de la aplicación y leída solo a través de una vista `security_barrier` que filtre por la administración del contexto.
  - **`security_invoker` solo aísla si las tablas que la vista lee tienen RLS,** y `ledger.tax_account_movement` no tiene `administration_id` en el ERD, aunque `docs/arquitectura/02` §5.1 lo exige en toda tabla con datos de una administración. Había dos opciones; en la H2 se decidió la (a):
    - **(a, decidida en la H2)** agregar `administration_id` a `tax_account_movement`, con FK compuesta (`obligation_id`, `administration_id`) a `tax_obligation` y su política RLS. Cierra el hueco también para quien lea los movimientos sin pasar por la vista, y permite que la FK de `credit_balance_use` hacia los movimientos sea compuesta (paso 1). Las otras tablas de `ledger` que tampoco tienen `administration_id` (`obligation_component`, `payment_allocation`, `revenue_distribution`) se tratan igual en el ERD 2.2;
    - **(b, descartada)** que la vista haga *join* con `tax_obligation`, que sí tiene RLS. Aísla la vista, pero no las lecturas directas de los movimientos.
  - El ERD 2.2 lo dice en la nota de la vista. El número de tablas no cambia (79).
  - **Descartado:** dejar la vista materializada «para consultas», que filtra datos entre administraciones.

## Riesgos

- **Errores al pasar el Doc. 05 al repositorio:** la revisión de R1 lo compara con el insumo, verificado por SHA‑256, y la revisión adversarial de lo implementado repite el recálculo de §4.3 (005‑04).
- **Quedan códigos viejos:** en R1 y R4 se buscan `IN‑`, `PR‑` y `OB‑06` a `OB‑10` en el Doc. 05, y en `docs/` se comprueba que `OB‑01` a `OB‑05` e `IN‑xx` conserven su significado de antes.
- **Plantilla e Ipiales se separan:**
  - en R3b, una comprobación de que las secciones nuevas tienen las mismas claves, con su resultado en el PR;
  - en B1, la mutación falla si una clave falta en uno de los dos.
- **Los anexos de R3b y las copias de B1 no coinciden** (P1): `diff` vacío al integrar R3b, y R3b congelado desde su H3.
- **La refactorización de B0 debilita una prueba:** B0 no cambia ningún resultado, y cuenta las pruebas antes y después, con el mismo número y todas en verde.
- **PR grandes** (R1 y B1): la justificación de Q12, y los comandos de `diff` para ver solo el cambio real.
- **El Excel trae datos reales o vínculos externos:** se inspecciona antes de subirlo, y si aparece algo así se detiene el PR (paso 3).
- **El resultado de §9.2 se evalúa a mano** mientras no exista el validador: se muestra regla por regla, y la feature de la opción 1.5.9 lo confirma con código.
- **Ventana sin validar** hasta la feature de la opción 1.5.9 (004‑13): las copias de B1 son la prueba de CJ01, y P1 evita que difieran.

## Verificaciones por PR

| PR | Verificación | Criterios |
|---|---|---|
| R2 | Cada ajuste del Doc. 04 §11 (los nueve puntos) y del Doc. 05 §10 aparece en el Mermaid, en la lista y en las relaciones; cada tabla nueva tiene su regla de `administration_id`, y las cuatro tablas de `ledger` que no lo tenían lo reciben con FK compuesta y RLS (P19); el número de tablas cuadra (79) | 005‑15 |
| R1 | Recálculo de §4.3 con aritmética exacta; búsqueda de códigos viejos; cada tabla citada existe en el ERD 2.2; 19 pendientes en §11 con su responsable | 005‑01 a 005‑07 |
| R3a | `diff` del Doc. 04 contra la v0.1: solo las secciones de 005‑08; sin «Wompi», «WP‑» ni «PASARELA», y CA‑4.14 a CA‑4.17 solo en la nota de reserva; ningún pendiente de la v0.1 perdido; CJ15 y CJ16 tal como los fija P17; tabla de CJ01 a CJ16 para Ipiales con el SHA‑256 de los YAML evaluados, igual al de R3b; SHA‑256 y tamaño del Excel | 005‑08, 005‑09, 005‑10, 005‑14, CJ15, CJ16 |
| R0 | El ADR 0034 sigue la plantilla de `liv-adr`, complementa al 0032 sin cambiar su decisión y queda como «Propuesto» hasta que la persona lo acepte | — |
| B0 | `./mvnw verify` en verde, con el mismo número de pruebas antes y después; los casos de carga y de validación de `1.0.casos.json` siguen corriendo | — |
| R3b | Claves iguales en las secciones nuevas de los dos YAML; valores de la plantilla según su tabla, con las desviaciones marcadas | 005‑11 a 005‑13 |
| B1 | `./mvnw verify` en verde (CJ01, compatibilidad, mutación, casos de carga y de validación de las dos versiones, meta‑prueba y `jsonschema.sh check`); `diff` de las copias contra R3b | CJ01, 005‑16 a 005‑18 |
| B1 (después de integrar) | La *release* `jsonschema-config-jurisdiccion-v1.1` existe, con sus SHA‑256 | 005‑19 |
| R4 | Ninguna referencia a «01–04» ni a «un futuro Módulo 5»; los prefijos de §5.3 existen en los documentos | 005‑20, 005‑21 |

Cada PR lleva la lente de legibilidad antes del *commit*. Como el cambio es crítico, lo implementado pasa por la revisión adversarial y las cuatro lentes de `liv-revision-4r` antes de abrir el PR (`AGENTS.md` §4.3).

## Aprobaciones de la persona (H2 dada el 2026-11-09)

- **H2: este plan (aprobado el 2026-11-09),** y en especial:
  - P1: el orden, el congelamiento de R3b desde su H3 y la referencia adelantada entre R1 y R3a;
  - las excepciones a 004‑08: P3 (`PENDIENTE` en una lista cerrada) y P7 (en un entero y en un código);
  - las desviaciones del insumo: P4, P5, P13, P16 y las tres de la tabla «Valores de la plantilla»;
  - P17: los ajustes a CJ15, CJ16 y CJ02, que precisan la decisión 8 de la H1. Aprobados en la H2; la redacción de CJ15 y CJ16 ya está en la spec (por delegación del orquestador);
  - P18, la decisión que registra el **ADR 0034**. El ADR se escribe como «Propuesto» en R0 y la persona lo acepta formalmente en la H3 de R0 (`liv-adr`);
  - P19, la vista de saldos sin vista materializada, con la opción (a) para el RLS de los movimientos (decidida en la H2);
  - P13, `jurisdiction_config_id` en la obligación y la regla de no derivar la exigibilidad con otra configuración.
- **§4.5, pruebas y compuertas:** B0 y B1 cambian las pruebas de esquema (el oráculo, `ENUM_PATHS` y la tabla de campos) por versión. No las debilitan: las de la 1.0 siguen iguales.
- **No hace falta la aprobación de §4.5 para un cambio incompatible:** el 1.1 es compatible con el 1.0 (005‑18).
- **H3 de R4:** el cambio de reglas de `AGENTS.md` y del árbol de `docs/arquitectura/01` §14.
- La integración de cada PR la hace la persona; la *release* 1.1 la publica `backend-main` al integrar B1.

## Revisión

- [x] **Revisión adversarial del plan, primera ronda** (2026-11-08), con dos revisores ciegos de nivel alto (`liv-revisor-critico`). Encontraron 1 crítico, 7 altos, 15 medios y 7 bajos (A: 1 · 3 · 7 · 4; B: 0 · 4 · 8 · 3).
  - **Confirmados por los dos y corregidos:**
    - el oráculo de la mutación contra la compatibilidad, en `bancos[]` y en `formato_por_banco[]` (paso 7 y P2);
    - el orden de integración, con R3 partido en R3a y R3b, y R3b congelado desde su H3 (P1);
    - `administration_id` en las tablas nuevas (paso 1);
    - `enforceable_from`, que repetía un dato, y la cita de §5.1 (P13);
    - los pendientes perdidos del Doc. 04 (paso 3);
    - los pendientes del Doc. 05 que no estaban (tabla, de 13 a 19);
    - los cambios a la meta‑prueba que el plan no declaraba, y P7 como excepción (B0, B1 y P7);
    - la unidad de `sancion_minima_uvt` (P8);
    - lo que faltaba en CJ15, CJ16 y CJ02 (P17);
    - las restricciones del ERD y los campos de la plantilla de cartera histórica (paso 1);
    - el diagnóstico de la decisión 5 (P13, paso 2 y pendiente 13).
  - **Encontrados por uno solo, verificados y corregidos:**
    - (A, crítico) el caso «versión desconocida» de `1.0.casos.json` usa `"1.1"`: comprobado en el archivo (P18 y R0);
    - (A) `credit_balance.available_amount` era un saldo guardado (P16);
    - (A) el reinicio del término tras la reorganización (pendiente 18);
    - (A) el error de cuenta en la decisión 4 de la spec («siete de $8.000» eran seis), corregido en su texto;
    - (B) que `PENDIENTE` en `sistema_determinacion` se leyera como «no declara» (P3 y P17);
    - (B) que P9 no se pudiera materializar sin ampliar `tax.penalty_rule` (paso 1);
    - (B) las columnas duplicadas de `interest_rate` y de `interest_calculation` (paso 1);
    - (B) quién escribe las tasas nacionales (P10 y pendiente 17);
    - (B) los valores de la plantilla (tabla) y `remitente` como texto libre (P8);
    - (B) el comentario de opciones de `redondeo_causacion.metodo` (P8);
    - (B) los rangos de antigüedad (pendiente 19);
    - (B) los roles fuera del §9, la salvedad *golden* de §5.3 y el número del §12.3 (pasos 2, 3 y 8);
    - (B) B1 sí se podía partir: la refactorización sale a B0.
  - **Descartado:** nada.
  - **Escalado a la persona:** los puntos de «Aprobaciones de la persona». P17 y P18 tocan una decisión de la H1 y un ADR aceptado.
- [x] **Segunda ronda, solo sobre lo corregido** (2026-11-08), con los mismos dos niveles. Encontraron 0 críticos, 5 altos, 13 medios y 2 bajos (A: 0 · 2 · 6 · 2; B: 0 · 3 · 7 · 0). Todo quedó corregido o escalado, sin descartar nada:
  - **Confirmados por los dos:**
    - una nota no puede cambiar el fondo de un ADR aceptado (`liv-adr`): R0 pasa a ser el ADR 0034, que complementa al 0032 (P15 y P18);
    - el Doc. 04 de R3a citaba un Excel que llegaba en R3b, y describía YAML que aún podían cambiar: el Excel pasa a R3a, y R3a espera a la H3 de R3b (P1);
    - la llave única de `interest_calculation` chocaba con los reversos y las simulaciones: ahora es un índice parcial (paso 1; su columna pasó a `reversed_by_movement_id` en la verificación independiente);
    - B1 dejaba sin probar `formato_por_banco[]`, y la compatibilidad fallaba con los casos de versión: casos de clase propios, orden «versión primero, mutación después» y exclusiones (paso 7);
    - el CHECK de signo no podía consultar otra tabla: ahora es una FK compuesta con `movement_sign` (paso 1);
    - las columnas derivadas de `interest_rate` y las de pesos de `penalty_rule`: se quitan o se reemplazan (paso 1, P13);
    - P13 no decía con qué configuración se deriva la exigibilidad: `jurisdiction_config_id` y el pendiente 14 (P13).
  - **Encontrados por uno solo, verificados y corregidos:**
    - (A, alto) la vista materializada se saltaba el RLS (P19, que decide el arquitecto);
    - (A) faltaba la segregación en `interest_rate` y `portfolio_cutoff`, y la cita «Doc. 04 §11.3» no existía (paso 1);
    - (A) limitar CJ15 y CJ16 a la 1.1 permitía saltárselas declarando `"1.0"` (P17);
    - (A) la tabla de la plantilla no seguía al `LEEME.md`: ahora lo sigue, con tres desviaciones marcadas para la H2;
    - (A) el CHECK de `assessment_id` y los orígenes futuros (paso 1);
    - (B) las pruebas que B0 cambia y no estaban listadas (paso 5);
    - (B) las FK que no pueden ser compuestas se validan en la transacción (paso 1);
    - (B) la concurrencia de los usos del saldo a favor (P16);
    - (B) la periodicidad del reporte del 20 %, un pendiente del Doc. 04 que se perdía (paso 3);
    - (B) la nota de `tasks.md`, que estaba desactualizada.
  - **Escalado a la persona** (`liv-revision-adversarial` §4: después de la segunda ronda seguían apareciendo altos). No hay tercera ronda. Las correcciones de la segunda ronda no las volvió a revisar un tercero; la persona decide si se aprueba la H2 así o si pide otra revisión. Lo que le toca decidir está en «Aprobaciones de la persona».
- [x] **Verificación independiente de la segunda ronda** (2026-11-09), con un revisor crítico que no participó en las rondas anteriores, sobre el *commit* `e7cdd5b`. Veredicto: apto para H2. Hizo 6 observaciones (0 críticas · 0 altas · 3 medias · 2 bajas · 1 nota). La persona decidió corregirlas antes de la H2, sin otra ronda adversarial:
  1. **(media) La llave de `interest_calculation`:** un índice único parcial no se puede diferir. La columna pasó a ser `reversed_by_movement_id`, que apunta al movimiento `REVERSAL`, como el `reversed_by` del insumo, y no al cálculo nuevo. Se fijó el orden dentro de la transacción: primero el reverso, después se marca el cálculo viejo y al final se inserta el nuevo (paso 1).
  2. **(media) La FK compuesta con `movement_sign`:** `ASSESSMENT_ADJUSTMENT` y `REVERSAL` no tienen un signo fijo. El catálogo admite `±`, el CHECK lo acepta con cualquier signo distinto de 0, y el signo y el componente de un `REVERSAL` frente a su original se validan en la transacción (paso 1).
  3. **(media) P19:** `security_invoker` no aísla si `tax_account_movement` no tiene RLS. P19 da al arquitecto dos opciones: (a) `administration_id` en los movimientos y en las demás tablas de `ledger` que no lo tienen, con FK compuesta y política RLS (recomendada); (b) un *join* con `tax_obligation`.
  4. **(baja) El orden entre proyectos:** R1 también espera la H3 de R3b y entra justo antes que R3a. «Enfoque» declara la segunda referencia adelantada: el SHA‑256 de los YAML de R3b citado en el §9.2 de R3a.
  5. **(baja) Las pruebas que cambia B0:** se agregan `JurisdictionConfigSchemas.forVersion`, donde se implementa P18, y `CommonCases.load`. Se aclara que B0 no puede demostrar P18 y que lo demuestra una prueba nueva de B1 (pasos 5 y 7).
  6. **(nota) La aceptación del ADR 0034:** la H2 aprueba la decisión, y la aceptación formal del ADR va en la H3 de R0 (paso 4, P15, P18 y «Aprobaciones»).
- [x] **Aprobación H2:** la persona que dirige el trabajo, 2026-11-09 (`AGENTS.md` §4.3), sobre el *commit* `33dd948`, con P1 a P19. En P19 eligió la opción (a): `administration_id` en todas las tablas de `ledger`. Aprobó también, según `AGENTS.md` §4.5, los cambios por versión de las pruebas de esquema de B0 y B1 (el oráculo, `ENUM_PATHS`, la tabla de campos y `JurisdictionConfigSchemas.forVersion`), que no debilitan las de la 1.0.
