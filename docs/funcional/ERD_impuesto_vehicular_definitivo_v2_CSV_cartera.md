# ERD definitivo propuesto — Impuesto vehicular

> Basado en el catálogo lógico inicial de 63 tablas y 563 campos (65 tablas desde la versión 2.1). Validación estructural; no supone aprobación jurídica ni despliegue de PostgreSQL.
>
> **Versión:** 2.1 — registra los ajustes al modelo que el responsable funcional aprobó en H2 de la feature 001 · Inicio de sesión (2026-11-01). Agrega `core.user_session` y `audit.import_cursor`, que dejan el catálogo en 65 tablas. Cambian `core.admin_user` y `audit.audit_event`. Además, documenta los esquemas técnicos `platform` y `bff`, que no son parte del catálogo funcional (ver «Esquemas técnicos»).

## Hallazgos prioritarios de validación

1. **Avalúos:** mantener la estructura `valuation_catalog` → `valuation_catalog_entry` → `vehicle_valuation` con `model_year` en la última; preservar encabezado y posición fuente, unidades y versión de archivo.
2. **Integridad territorial:** FK compuestas o validación transaccional para evitar mezclar `administration_id` de distintas entidades en liquidaciones, pagos y expedientes.
3. **Liquidación y obligación:** impedir doble obligación activa del mismo sujeto/vehículo/vigencia/concepto salvo responsabilidad solidaria o rectificación formal; distinguir `assessment` técnico de título legal.
4. **Pagos:** validación de que la suma de imputaciones no exceda el pago confirmado y que el componente corresponda a la obligación.
5. **Años fiscales:** garantizar que `vehicle_reference_match.fiscal_year` coincida con `valuation_catalog.fiscal_year` de la referencia seleccionada.
6. **Intereses y sanciones:** registrar versiones normativas, período, tasa y bases; ningún cálculo es automáticamente una sanción exigible.
7. **Prescripción:** revisar título, hecho generador del cómputo, notificaciones e interrupciones; no automatizar declaración jurídica.
8. **Embargos:** impedir que monto ejecutado exceda límite permitido; controlar bien, destinatario, consignación y levantamiento.
9. **Notificaciones:** diferenciar fecha de expedición, entrega, notificación legal y ejecutoria con prueba.
10. **Importación CSV de avalúos:** staging con archivo inmutable (hash), separador, codificación, multiplicador de unidades, vista previa, validación, dos roles (carga/aprobación) y publicación atómica; no permitir sobrescritura del catálogo publicado.
11. **Apertura de cartera histórica:** lote CSV/Excel independiente del catálogo ministerial; importar como saldo recibido, conciliar y aprobar antes de contabilizar; soportar vigencia, sujeto, vehículo, capital, sanciones e intereses con fecha de corte; vincular al título y expediente si existen.
12. **Faltantes para producción:** catálogos de estados, controles de concurrencia, RLS, políticas de retención y vínculos de actos/facturas al título ejecutivo.

## ERD general (relaciones principales)

```mermaid
erDiagram
    core_vehicle ||--o{ core_vehicle_registration : has
    core_vehicle ||--o{ core_vehicle_taxpayer_link : has
    core_taxpayer ||--o{ core_vehicle_taxpayer_link : linked
    valuation_valuation_catalog ||--o{ valuation_valuation_catalog_entry : contains
    valuation_valuation_catalog ||--o{ valuation_valuation_import_batch : has_imports
    valuation_valuation_import_batch ||--o{ valuation_valuation_import_row : stages
    valuation_valuation_import_row ||--o{ valuation_valuation_import_error : reports
    valuation_vehicle_reference ||--o{ valuation_valuation_catalog_entry : normalizes
    valuation_valuation_catalog_entry ||--o{ valuation_vehicle_valuation : values
    valuation_vehicle_valuation ||--o{ valuation_vehicle_reference_match : matched
    core_vehicle ||--o{ valuation_vehicle_reference_match : mapped
    valuation_vehicle_valuation ||--o{ tax_tax_assessment : appraised
    tax_tax_rate ||--o{ tax_tax_assessment : priced
    tax_tax_assessment ||--o{ ledger_tax_obligation : assessed
    ledger_tax_obligation ||--o{ ledger_obligation_component : divided
    ledger_tax_obligation ||--o{ ledger_tax_account_movement : booked
    ledger_payment ||--o{ ledger_payment_allocation : allocated
    ledger_tax_obligation ||--o{ ledger_payment_allocation : receives
    ledger_tax_obligation ||--o{ portfolio_prescription_event : may_affect
    core_tax_administration ||--o{ portfolio_opening_balance_batch : receives_opening_balance
    portfolio_opening_balance_batch ||--o{ portfolio_opening_balance_row : stages
    portfolio_opening_balance_row ||--o{ portfolio_opening_balance_error : validates
    portfolio_opening_balance_row ||--o{ portfolio_opening_balance_application : posts
    ledger_tax_obligation ||--o{ portfolio_opening_balance_application : maps_obligation
    ledger_tax_account_movement ||--o{ portfolio_opening_balance_application : maps_movement
    collection_collection_case ||--o{ collection_collection_case_obligation : contains
    ledger_tax_obligation ||--o{ collection_collection_case_obligation : included
    collection_collection_case ||--o{ collection_payment_order : orders
    collection_collection_case ||--o{ enforcement_precautionary_measure : attaches
    enforcement_debtor_asset ||--o{ enforcement_precautionary_measure : affects
```

## CORE — Registro automotor y contribuyentes (10 tablas)

```mermaid
erDiagram
    core_territorial_entity {
        UUID id PK
        VARCHAR_20 dane_code
        VARCHAR_150 name
        VARCHAR_20 entity_type
        UUID parent_id FK
        BOOLEAN active
    }
    core_tax_administration {
        UUID id PK
        UUID territorial_entity_id FK
        VARCHAR_150 name
        VARCHAR_20 nit
        VARCHAR_200 address
        BOOLEAN active
    }
    core_taxpayer {
        UUID id PK
        VARCHAR_20 document_type
        VARCHAR_30 document_number
        VARCHAR_200 full_name
        VARCHAR_20 taxpayer_type
        VARCHAR_200 email
        VARCHAR_30 phone
        VARCHAR_30 status
        TIMESTAMPTZ created_at
    }
    core_taxpayer_address {
        UUID id PK
        UUID taxpayer_id FK
        UUID municipality_id FK
        VARCHAR_250 address_line
        VARCHAR_20 postal_code
        DATE valid_from
        DATE valid_to
        BOOLEAN primary_address
    }
    core_vehicle {
        UUID id PK
        VARCHAR_15 plate
        VARCHAR_50 vin
        VARCHAR_50 engine_number
        VARCHAR_50 chassis_number
        VARCHAR_100 brand
        VARCHAR_150 vehicle_line
        VARCHAR_100 vehicle_class
        VARCHAR_50 body_type
        VARCHAR_30 service_type
        VARCHAR_30 fuel_type
        SMALLINT model_year
        INTEGER engine_cc
        NUMERIC_12_2 load_capacity
        INTEGER passenger_capacity
        DATE first_registration_date
        VARCHAR_30 status
        TIMESTAMPTZ created_at
    }
    core_vehicle_registration {
        UUID id PK
        UUID vehicle_id FK
        UUID municipality_id FK
        VARCHAR_100 transit_agency
        VARCHAR_50 license_number
        DATE registered_at
        DATE valid_from
        DATE valid_to
        VARCHAR_50 source_reference
    }
    core_vehicle_taxpayer_link {
        UUID id PK
        UUID vehicle_id FK
        UUID taxpayer_id FK
        VARCHAR_20 relation_type
        NUMERIC_7_4 ownership_pct
        DATE valid_from
        DATE valid_to
        VARCHAR_100 supporting_document
        VARCHAR_30 verification_status
    }
    core_vehicle_tax_status {
        UUID id PK
        UUID vehicle_id FK
        VARCHAR_30 tax_status
        VARCHAR_50 legal_basis
        DATE valid_from
        DATE valid_to
        UUID supporting_document_id FK
        VARCHAR_30 approval_status
    }
    core_admin_user {
        UUID id PK
        UUID administration_id FK
        VARCHAR_150 full_name
        VARCHAR_200 email
        VARCHAR_30 status
        VARCHAR_255 identity_issuer
        VARCHAR_255 identity_subject
        TIMESTAMPTZ last_login_at
    }
    core_user_session {
        VARCHAR_100 sid PK
        UUID user_id FK
        UUID administration_id FK
        VARCHAR_255 identity_issuer
        VARCHAR_255 identity_subject
        TIMESTAMPTZ started_at
        TIMESTAMPTZ ended_at
        VARCHAR_20 end_reason
    }
    core_territorial_entity ||--o{ core_territorial_entity : parent_id
    core_territorial_entity ||--o{ core_tax_administration : territorial_entity_id
    core_taxpayer ||--o{ core_taxpayer_address : taxpayer_id
    core_territorial_entity ||--o{ core_taxpayer_address : municipality_id
    core_vehicle ||--o{ core_vehicle_registration : vehicle_id
    core_territorial_entity ||--o{ core_vehicle_registration : municipality_id
    core_vehicle ||--o{ core_vehicle_taxpayer_link : vehicle_id
    core_taxpayer ||--o{ core_vehicle_taxpayer_link : taxpayer_id
    core_vehicle ||--o{ core_vehicle_tax_status : vehicle_id
    core_tax_administration ||--o{ core_admin_user : administration_id
    core_admin_user ||--o{ core_user_session : user_id
```

**Tablas y funciones:**

- `core.territorial_entity` — Catálogo DANE de departamentos, distritos y municipios.
- `core.tax_administration` — Autoridad competente que administra el impuesto.
- `core.taxpayer` — Contribuyentes identificados por documento.
- `core.taxpayer_address` — Historial de direcciones de contribuyentes.
- `core.vehicle` — Inventario maestro de automotores.
- `core.vehicle_registration` — Historial de matrícula, radicación y traslado.
- `core.vehicle_taxpayer_link` — Historial de propietarios, poseedores y relaciones con vehículo.
- `core.vehicle_tax_status` — Clasificación histórica de sujeción, exclusión y beneficios por vehículo.
- `core.admin_user` — Usuarios funcionarios y responsables de actos y auditoría. Feature 001:
  - `status` vale `ACTIVE` o `INACTIVE`; un usuario `INACTIVE` no entra (001-05);
  - `identity_issuer` e `identity_subject` son el vínculo con el proveedor de identidad (emisor y `sub`), único por usuario. LIV identifica al usuario por su `id`, no por el proveedor (001-21);
  - `last_login_at` es el último inicio de sesión aceptado.
- `core.user_session` — Sesiones de los usuarios (feature 001), una por sesión del proveedor (`sid`). Apunta a su usuario con la FK compuesta `(user_id, administration_id, identity_issuer, identity_subject)` → `core.admin_user`. La abre `StartSession` y la cierra `EndSession` con `end_reason`: `LOGOUT` (salir), `IDLE` (inactividad), `ABSOLUTE` (duración máxima) o `PROVIDER` (el proveedor la cerró). Cerrarla dos veces no cambia nada. La FK compuesta impide apuntar a un usuario de otra administración o a otra identidad.
- `core.territorial_entity.entity_type` (feature 001) vale `DEPARTAMENTO`, `DISTRITO_CAPITAL`, `DISTRITO` o `MUNICIPIO`, los mismos tipos de `jurisdiccion.tipo` de los YAML de jurisdicción.

**Relaciones externas:**

- `core.vehicle_tax_status.supporting_document_id` → `documents.document`

## DOCUMENTS — Normas y expediente documental (5 tablas)

```mermaid
erDiagram
    documents_legal_instrument {
        UUID id PK
        VARCHAR_50 instrument_type
        VARCHAR_100 instrument_number
        DATE issue_date
        DATE effective_from
        DATE effective_to
        VARCHAR_250 issuing_authority
        TEXT description
        VARCHAR_500 source_url
    }
    documents_document {
        UUID id PK
        VARCHAR_200 file_name
        VARCHAR_100 mime_type
        VARCHAR_500 storage_key
        VARCHAR_64 sha256
        VARCHAR_40 document_type
        TIMESTAMPTZ uploaded_at
    }
    documents_legal_act {
        UUID id PK
        UUID administration_id FK
        VARCHAR_60 act_number
        VARCHAR_50 act_type
        DATE issued_at
        DATE enforceable_at
        VARCHAR_30 status
        UUID signed_document_id FK
        UUID signed_by FK
    }
    documents_notification {
        UUID id PK
        UUID legal_act_id FK
        UUID taxpayer_id FK
        VARCHAR_40 notification_method
        DATE sent_at
        DATE delivered_at
        DATE legally_notified_at
        VARCHAR_40 result
        UUID evidence_document_id FK
    }
    documents_legal_proceeding {
        UUID id PK
        UUID case_id FK
        UUID obligation_id FK
        VARCHAR_40 proceeding_type
        DATE filed_at
        UUID decision_act_id FK
        VARCHAR_30 status
        DATE legal_deadline
    }
    documents_document ||--o{ documents_legal_act : signed_document_id
    documents_legal_act ||--o{ documents_notification : legal_act_id
    documents_document ||--o{ documents_notification : evidence_document_id
    documents_legal_act ||--o{ documents_legal_proceeding : decision_act_id
```

**Tablas y funciones:**

- `documents.legal_instrument` — Registro de normas, resoluciones y actos regulatorios.
- `documents.document` — Repositorio de archivos electrónicos con integridad criptográfica.
- `documents.legal_act` — Actos administrativos con firma, estado y ejecutoria.
- `documents.notification` — Prueba de envío, entrega y notificación legal de actos.
- `documents.legal_proceeding` — Recursos, excepciones y controversias que afectan obligaciones o expedientes.

**Relaciones externas:**

- `documents.legal_act.administration_id` → `core.tax_administration`
- `documents.legal_act.signed_by` → `core.admin_user`
- `documents.notification.taxpayer_id` → `core.taxpayer`
- `documents.legal_proceeding.case_id` → `collection.collection_case`
- `documents.legal_proceeding.obligation_id` → `ledger.tax_obligation`

## VALUATION — Avalúos ministeriales e importación CSV (8 tablas)

```mermaid
erDiagram
    valuation_valuation_catalog {
        UUID id PK
        SMALLINT fiscal_year FK
        UUID legal_instrument_id FK
        VARCHAR_100 catalog_name
        VARCHAR_64 file_sha256
        VARCHAR_500 source_file_uri
        VARCHAR_500 source_url
        VARCHAR_30 import_status
        TIMESTAMPTZ imported_at
        UUID approved_by FK
        TIMESTAMPTZ approved_at
        INTEGER version_number
    }
    valuation_vehicle_reference {
        UUID id PK
        VARCHAR_100 brand
        VARCHAR_150 vehicle_line
        VARCHAR_60 vehicle_class
        INTEGER engine_cc
        VARCHAR_50 body_type
        VARCHAR_30 fuel_type
        VARCHAR_30 service_type
        TEXT notes
    }
    valuation_valuation_catalog_entry {
        UUID id PK
        UUID catalog_id FK
        UUID vehicle_reference_id FK
        VARCHAR_50 ministry_reference_code
        VARCHAR_100 original_brand
        VARCHAR_150 original_line
        VARCHAR_60 original_vehicle_class
        INTEGER original_engine_cc
        VARCHAR_100 source_sheet
        INTEGER source_row_number
        VARCHAR_64 row_checksum
    }
    valuation_vehicle_valuation {
        UUID id PK
        UUID catalog_entry_id FK
        SMALLINT model_year
        NUMERIC_18_2 commercial_value
        VARCHAR_50 source_value
        VARCHAR_30 source_column
        VARCHAR_30 valuation_method
        UUID similar_valuation_id FK
        UUID supporting_document_id FK
        VARCHAR_30 status
    }
    valuation_vehicle_reference_match {
        UUID id PK
        UUID vehicle_id FK
        UUID vehicle_valuation_id FK
        SMALLINT fiscal_year FK
        VARCHAR_30 match_method
        NUMERIC_5_2 confidence_score
        VARCHAR_30 review_status
        UUID reviewed_by FK
        TIMESTAMPTZ reviewed_at
    }
    valuation_valuation_import_batch {
        UUID id PK
        UUID catalog_id FK
        VARCHAR_200 original_filename
        VARCHAR_64 file_sha256
        VARCHAR_20 encoding
        VARCHAR_10 delimiter
        VARCHAR_30 decimal_separator
        INTEGER monetary_multiplier
        INTEGER total_rows
        INTEGER valid_rows
        INTEGER error_rows
        INTEGER warning_rows
        VARCHAR_30 status
        UUID uploaded_by FK
        UUID approved_by FK
        TIMESTAMPTZ uploaded_at
        TIMESTAMPTZ approved_at
        VARCHAR_500 source_storage_key
    }
    valuation_valuation_import_row {
        UUID id PK
        UUID import_batch_id FK
        INTEGER source_row_number
        JSONB original_data
        JSONB normalized_data
        VARCHAR_30 validation_status
        VARCHAR_64 row_sha256
    }
    valuation_valuation_import_error {
        UUID id PK
        UUID import_row_id FK
        VARCHAR_50 error_code
        VARCHAR_100 field_name
        TEXT description
        VARCHAR_20 severity
        TIMESTAMPTZ created_at
    }
    valuation_valuation_catalog ||--o{ valuation_valuation_import_batch : catalog_id
    valuation_valuation_import_batch ||--o{ valuation_valuation_import_row : import_batch_id
    valuation_valuation_import_row ||--o{ valuation_valuation_import_error : import_row_id
    valuation_valuation_catalog ||--o{ valuation_valuation_catalog_entry : catalog_id
    valuation_vehicle_reference ||--o{ valuation_valuation_catalog_entry : vehicle_reference_id
    valuation_valuation_catalog_entry ||--o{ valuation_vehicle_valuation : catalog_entry_id
    valuation_vehicle_valuation ||--o{ valuation_vehicle_valuation : similar_valuation_id
    valuation_vehicle_valuation ||--o{ valuation_vehicle_reference_match : vehicle_valuation_id
```

**Tablas y funciones:**

- `valuation.valuation_catalog` — Versión e importación de cada tabla oficial de avalúos del Ministerio.
- `valuation.vehicle_reference` — Identidad técnica normalizada de variantes vehiculares.
- `valuation.valuation_catalog_entry` — Representa una fila original del catálogo ministerial en una vigencia.
- `valuation.vehicle_valuation` — Avalúo oficial por referencia del catálogo anual y año modelo.
- `valuation.vehicle_reference_match` — Aprobación de asociación entre un vehículo real y su avalúo por vigencia.
- `valuation.valuation_import_batch` — Un archivo CSV recibido, su origen, formato, unidades monetarias, totales de validación y aprobación.
- `valuation.valuation_import_row` — Fila de staging con datos originales y normalizados; no afecta los avalúos publicados.
- `valuation.valuation_import_error` — Errores y advertencias por fila/campo durante validación.

**Flujo del importador:** `UPLOADED` → `VALIDATING` → `VALIDATED` → `APPROVED` → `PUBLISHED`, con `REJECTED` / `FAILED` cuando corresponda. Una fila inválida bloquea la publicación del lote; una advertencia requiere política de aprobación. Solo se materializa una versión nueva de catálogo después de aprobación y publicación transaccional. Las filas del CSV con columnas por año modelo se normalizan a `vehicle_valuation` conservando encabezado, fila y unidad de origen. Los valores publicados no se sobrescriben: una corrección produce otra versión del catálogo.

**Relaciones externas:**

- `valuation.valuation_catalog.fiscal_year` → `tax.fiscal_year`
- `valuation.valuation_catalog.legal_instrument_id` → `documents.legal_instrument`
- `valuation.valuation_catalog.approved_by` → `core.admin_user`
- `valuation.vehicle_valuation.supporting_document_id` → `documents.document`
- `valuation.vehicle_reference_match.vehicle_id` → `core.vehicle`
- `valuation.vehicle_reference_match.fiscal_year` → `tax.fiscal_year`
- `valuation.vehicle_reference_match.reviewed_by` → `core.admin_user`
- `valuation.valuation_import_batch.uploaded_by` → `core.admin_user`
- `valuation.valuation_import_batch.approved_by` → `core.admin_user`

## TAX — Determinación tributaria (8 tablas)

```mermaid
erDiagram
    tax_fiscal_year {
        SMALLINT year PK
        DATE starts_on
        DATE ends_on
        VARCHAR_20 status
        BOOLEAN assessments_enabled
    }
    tax_tax_rate {
        UUID id PK
        SMALLINT fiscal_year FK
        UUID legal_instrument_id FK
        UUID administration_id FK
        VARCHAR_40 vehicle_category
        NUMERIC_18_2 base_min
        NUMERIC_18_2 base_max
        NUMERIC_9_6 rate
        DATE valid_from
        DATE valid_to
    }
    tax_tax_calendar {
        UUID id PK
        UUID administration_id FK
        SMALLINT fiscal_year FK
        VARCHAR_40 taxpayer_group
        DATE discount_deadline
        DATE payment_deadline
        UUID legal_instrument_id FK
    }
    tax_tax_benefit {
        UUID id PK
        UUID administration_id FK
        SMALLINT fiscal_year FK
        UUID legal_instrument_id FK
        VARCHAR_40 benefit_type
        NUMERIC_9_6 benefit_rate
        NUMERIC_18_2 maximum_amount
        DATE valid_from
        DATE valid_to
    }
    tax_penalty_rule {
        UUID id PK
        UUID administration_id FK
        SMALLINT fiscal_year FK
        UUID legal_instrument_id FK
        VARCHAR_40 penalty_type
        VARCHAR_40 calculation_method
        NUMERIC_9_6 rate
        NUMERIC_18_2 minimum_amount
        NUMERIC_18_2 maximum_amount
    }
    tax_tax_assessment {
        UUID id PK
        UUID vehicle_id FK
        UUID taxpayer_id FK
        UUID administration_id FK
        SMALLINT fiscal_year FK
        UUID valuation_id FK
        UUID tax_rate_id FK
        UUID calendar_id FK
        DATE accrual_date
        DATE due_date
        NUMERIC_18_2 taxable_base
        NUMERIC_9_6 applied_rate
        SMALLINT taxable_months
        NUMERIC_18_2 gross_tax
        NUMERIC_18_2 discount
        NUMERIC_18_2 net_tax
        VARCHAR_30 status
        INTEGER version
        TIMESTAMPTZ created_at
    }
    tax_tax_declaration {
        UUID id PK
        UUID assessment_id FK
        VARCHAR_60 declaration_number
        VARCHAR_30 declaration_type
        DATE submitted_at
        NUMERIC_18_2 declared_tax
        NUMERIC_18_2 declared_penalty
        VARCHAR_30 status
        UUID supporting_document_id FK
    }
    tax_official_determination {
        UUID id PK
        UUID assessment_id FK
        UUID legal_act_id FK
        VARCHAR_40 determination_type
        NUMERIC_18_2 determined_tax
        NUMERIC_18_2 determined_penalty
        DATE enforceable_date
        VARCHAR_30 status
    }
    tax_fiscal_year ||--o{ tax_tax_rate : fiscal_year
    tax_fiscal_year ||--o{ tax_tax_calendar : fiscal_year
    tax_fiscal_year ||--o{ tax_tax_benefit : fiscal_year
    tax_fiscal_year ||--o{ tax_penalty_rule : fiscal_year
    tax_fiscal_year ||--o{ tax_tax_assessment : fiscal_year
    tax_tax_rate ||--o{ tax_tax_assessment : tax_rate_id
    tax_tax_calendar ||--o{ tax_tax_assessment : calendar_id
    tax_tax_assessment ||--o{ tax_tax_declaration : assessment_id
    tax_tax_assessment ||--o{ tax_official_determination : assessment_id
```

**Tablas y funciones:**

- `tax.fiscal_year` — Apertura y cierre de vigencias fiscales.
- `tax.tax_rate` — Tarifas y umbrales por vigencia, jurisdicción y clase.
- `tax.tax_calendar` — Vencimientos y fechas de descuentos por administración y año.
- `tax.tax_benefit` — Descuentos, exenciones y alivios tributarios autorizados.
- `tax.penalty_rule` — Reglas de sanciones tributarias legalmente aplicables.
- `tax.tax_assessment` — Cálculo versionado del impuesto sobre un vehículo y vigencia.
- `tax.tax_declaration` — Declaraciones privadas radicadas por el contribuyente.
- `tax.official_determination` — Liquidación o determinación oficial distinta de cálculo interno.

**Relaciones externas:**

- `tax.tax_rate.legal_instrument_id` → `documents.legal_instrument`
- `tax.tax_rate.administration_id` → `core.tax_administration`
- `tax.tax_calendar.administration_id` → `core.tax_administration`
- `tax.tax_calendar.legal_instrument_id` → `documents.legal_instrument`
- `tax.tax_benefit.administration_id` → `core.tax_administration`
- `tax.tax_benefit.legal_instrument_id` → `documents.legal_instrument`
- `tax.penalty_rule.administration_id` → `core.tax_administration`
- `tax.penalty_rule.legal_instrument_id` → `documents.legal_instrument`
- `tax.tax_assessment.vehicle_id` → `core.vehicle`
- `tax.tax_assessment.taxpayer_id` → `core.taxpayer`
- `tax.tax_assessment.administration_id` → `core.tax_administration`
- `tax.tax_assessment.valuation_id` → `valuation.vehicle_valuation`
- `tax.tax_declaration.supporting_document_id` → `documents.document`
- `tax.official_determination.legal_act_id` → `documents.legal_act`

## LEDGER — Cuenta corriente y recaudo (7 tablas)

```mermaid
erDiagram
    ledger_tax_obligation {
        UUID id PK
        UUID assessment_id FK
        UUID taxpayer_id FK
        UUID vehicle_id FK
        SMALLINT fiscal_year FK
        UUID administration_id FK
        DATE due_date
        DATE legally_payable_from
        NUMERIC_18_2 original_principal
        VARCHAR_30 status
        TIMESTAMPTZ created_at
    }
    ledger_obligation_component {
        UUID id PK
        UUID obligation_id FK
        VARCHAR_30 component_type
        NUMERIC_18_2 original_amount
        VARCHAR_30 status
        UUID source_document_id FK
    }
    ledger_tax_account_movement {
        UUID id PK
        UUID obligation_id FK
        UUID component_id FK
        VARCHAR_40 movement_type
        NUMERIC_18_2 signed_amount
        DATE effective_date
        TIMESTAMPTZ posted_at
        UUID source_document_id FK
        VARCHAR_80 external_reference
        UUID reversal_of_id FK
    }
    ledger_payment {
        UUID id PK
        UUID taxpayer_id FK
        UUID administration_id FK
        VARCHAR_80 bank_reference
        VARCHAR_40 payment_method
        NUMERIC_18_2 amount
        DATE payment_date
        TIMESTAMPTZ confirmed_at
        VARCHAR_30 status
        VARCHAR_80 idempotency_key
    }
    ledger_payment_allocation {
        UUID id PK
        UUID payment_id FK
        UUID obligation_id FK
        UUID component_id FK
        NUMERIC_18_2 allocated_amount
        TIMESTAMPTZ applied_at
        UUID account_movement_id FK
    }
    ledger_credit_balance {
        UUID id PK
        UUID taxpayer_id FK
        UUID administration_id FK
        NUMERIC_18_2 available_amount
        VARCHAR_30 status
        UUID source_document_id FK
    }
    ledger_revenue_distribution {
        UUID id PK
        UUID payment_allocation_id FK
        UUID beneficiary_entity_id FK
        NUMERIC_9_6 share_rate
        NUMERIC_18_2 distributed_amount
        DATE transfer_date
        VARCHAR_30 status
    }
    ledger_tax_obligation ||--o{ ledger_obligation_component : obligation_id
    ledger_tax_obligation ||--o{ ledger_tax_account_movement : obligation_id
    ledger_obligation_component ||--o{ ledger_tax_account_movement : component_id
    ledger_tax_account_movement ||--o{ ledger_tax_account_movement : reversal_of_id
    ledger_payment ||--o{ ledger_payment_allocation : payment_id
    ledger_tax_obligation ||--o{ ledger_payment_allocation : obligation_id
    ledger_obligation_component ||--o{ ledger_payment_allocation : component_id
    ledger_tax_account_movement ||--o{ ledger_payment_allocation : account_movement_id
    ledger_payment_allocation ||--o{ ledger_revenue_distribution : payment_allocation_id
```

**Tablas y funciones:**

- `ledger.tax_obligation` — Obligación individual por vehículo, vigencia y sujeto con control de exigibilidad.
- `ledger.obligation_component` — Desglosa impuesto, sanciones, intereses, costas y otros rubros.
- `ledger.tax_account_movement` — Libro mayor inmutable de débitos, créditos y ajustes por obligación.
- `ledger.payment` — Recaudos confirmados y conciliables.
- `ledger.payment_allocation` — Imputación de cada pago a deuda y concepto.
- `ledger.credit_balance` — Saldos a favor disponibles y su disposición.
- `ledger.revenue_distribution` — Distribución del recaudo tributario según participaciones territoriales.

**Relaciones externas:**

- `ledger.tax_obligation.assessment_id` → `tax.tax_assessment`
- `ledger.tax_obligation.taxpayer_id` → `core.taxpayer`
- `ledger.tax_obligation.vehicle_id` → `core.vehicle`
- `ledger.tax_obligation.fiscal_year` → `tax.fiscal_year`
- `ledger.tax_obligation.administration_id` → `core.tax_administration`
- `ledger.obligation_component.source_document_id` → `documents.document`
- `ledger.tax_account_movement.source_document_id` → `documents.document`
- `ledger.payment.taxpayer_id` → `core.taxpayer`
- `ledger.payment.administration_id` → `core.tax_administration`
- `ledger.credit_balance.taxpayer_id` → `core.taxpayer`
- `ledger.credit_balance.administration_id` → `core.tax_administration`
- `ledger.credit_balance.source_document_id` → `documents.document`
- `ledger.revenue_distribution.beneficiary_entity_id` → `core.territorial_entity`

## PORTFOLIO — Mora, sanciones y prescripción (11 tablas)

```mermaid
erDiagram
    portfolio_interest_rate {
        UUID id PK
        VARCHAR_40 rate_type
        DATE valid_from
        DATE valid_to
        NUMERIC_12_8 annual_effective_rate
        VARCHAR_100 issuing_authority
        UUID legal_instrument_id FK
        VARCHAR_500 source_url
    }
    portfolio_interest_calculation {
        UUID id PK
        UUID obligation_id FK
        UUID interest_rate_id FK
        DATE mora_start_date
        DATE calculation_date
        NUMERIC_18_2 principal_balance
        INTEGER days_in_arrears
        NUMERIC_12_8 applied_annual_rate
        NUMERIC_18_2 interest_amount
        VARCHAR_40 rule_version
        TIMESTAMPTZ created_at
    }
    portfolio_interest_calculation_detail {
        UUID id PK
        UUID calculation_id FK
        DATE period_start
        DATE period_end
        NUMERIC_18_2 capital
        NUMERIC_12_8 applied_rate
        INTEGER days
        NUMERIC_18_2 amount
    }
    portfolio_penalty_assessment {
        UUID id PK
        UUID obligation_id FK
        UUID penalty_rule_id FK
        UUID legal_act_id FK
        NUMERIC_18_2 base_amount
        NUMERIC_18_2 penalty_amount
        DATE assessed_at
        VARCHAR_30 status
    }
    portfolio_prescription_event {
        UUID id PK
        UUID obligation_id FK
        UUID legal_act_id FK
        VARCHAR_40 event_type
        DATE event_date
        DATE notification_date
        VARCHAR_30 legal_effect
        DATE previous_deadline
        DATE proposed_deadline
        VARCHAR_30 validation_status
    }
    portfolio_prescription_evaluation {
        UUID id PK
        UUID obligation_id FK
        DATE evaluated_at
        DATE calculated_deadline
        VARCHAR_30 risk_level
        VARCHAR_30 legal_status
        UUID reviewed_by FK
        TEXT legal_reason
    }
    portfolio_collection_portfolio_snapshot {
        UUID id PK
        UUID obligation_id FK
        DATE snapshot_date
        NUMERIC_18_2 principal_balance
        NUMERIC_18_2 penalty_balance
        NUMERIC_18_2 interest_balance
        NUMERIC_18_2 total_balance
        INTEGER overdue_days
        VARCHAR_30 aging_bucket
    }
    portfolio_opening_balance_batch {
        UUID id PK
        UUID administration_id FK
        VARCHAR_200 original_filename
        VARCHAR_64 file_sha256
        DATE cutoff_date
        VARCHAR_30 status
        INTEGER total_rows
        INTEGER accepted_rows
        INTEGER rejected_rows
        UUID uploaded_by FK
        UUID approved_by FK
        TIMESTAMPTZ created_at
        TIMESTAMPTZ approved_at
    }
    portfolio_opening_balance_row {
        UUID id PK
        UUID batch_id FK
        INTEGER source_row_number
        VARCHAR_15 plate
        VARCHAR_30 taxpayer_document
        SMALLINT fiscal_year
        NUMERIC_18_2 tax_principal
        NUMERIC_18_2 penalty_balance
        NUMERIC_18_2 interest_balance
        NUMERIC_18_2 other_balance
        DATE original_due_date
        DATE balance_cutoff_date
        JSONB original_data
        VARCHAR_30 validation_status
    }
    portfolio_opening_balance_error {
        UUID id PK
        UUID opening_row_id FK
        VARCHAR_50 error_code
        VARCHAR_100 field_name
        TEXT error_message
        VARCHAR_20 severity
    }
    portfolio_opening_balance_application {
        UUID id PK
        UUID opening_row_id FK
        UUID obligation_id FK
        UUID account_movement_id FK
        VARCHAR_30 application_type
        NUMERIC_18_2 applied_amount
        TIMESTAMPTZ applied_at
    }
    portfolio_opening_balance_batch ||--o{ portfolio_opening_balance_row : contains
    portfolio_opening_balance_row ||--o{ portfolio_opening_balance_error : reports
    portfolio_opening_balance_row ||--o{ portfolio_opening_balance_application : applies
    portfolio_interest_rate ||--o{ portfolio_interest_calculation : interest_rate_id
    ledger_tax_obligation ||--o{ portfolio_opening_balance_application : maps_obligation
    ledger_tax_account_movement ||--o{ portfolio_opening_balance_application : maps_movement
    portfolio_interest_calculation ||--o{ portfolio_interest_calculation_detail : calculation_id
```

**Tablas y funciones:**

- `portfolio.interest_rate` — Tasas certificadas y parámetros para liquidar mora.
- `portfolio.interest_calculation` — Ejecución de cálculo de intereses a una fecha determinada.
- `portfolio.interest_calculation_detail` — Trazabilidad de tramos de capital y reglas dentro del cálculo.
- `portfolio.penalty_assessment` — Registro de sanciones determinadas y sus soportes.
- `portfolio.prescription_event` — Hechos con potencial efecto sobre el cómputo de prescripción.
- `portfolio.prescription_evaluation` — Dictamen trazable sobre término de prescripción por obligación.
- `portfolio.collection_portfolio_snapshot` — Corte histórico de saldos y antigüedad para reportes.

- `portfolio.opening_balance_batch` — Lote de migración de cartera y conciliación de totales, con separación entre operador y aprobador.
- `portfolio.opening_balance_row` — Fila íntegra de la fuente, componentes de deuda, identificación y fecha de corte.
- `portfolio.opening_balance_error` — Inconsistencias y advertencias por fila, sin modificar los datos originales.
- `portfolio.opening_balance_application` — Trazabilidad de cada aplicación aprobada sobre obligación y movimiento financiero, sin duplicar saldos.

**Relaciones externas:**

- `portfolio.interest_rate.legal_instrument_id` → `documents.legal_instrument`
- `portfolio.interest_calculation.obligation_id` → `ledger.tax_obligation`
- `portfolio.penalty_assessment.obligation_id` → `ledger.tax_obligation`
- `portfolio.penalty_assessment.penalty_rule_id` → `tax.penalty_rule`
- `portfolio.penalty_assessment.legal_act_id` → `documents.legal_act`
- `portfolio.prescription_event.obligation_id` → `ledger.tax_obligation`
- `portfolio.prescription_event.legal_act_id` → `documents.legal_act`
- `portfolio.prescription_evaluation.obligation_id` → `ledger.tax_obligation`
- `portfolio.prescription_evaluation.reviewed_by` → `core.admin_user`
- `portfolio.collection_portfolio_snapshot.obligation_id` → `ledger.tax_obligation`
- `portfolio.opening_balance_batch.administration_id` → `core.tax_administration`
- `portfolio.opening_balance_batch.uploaded_by` → `core.admin_user`
- `portfolio.opening_balance_batch.approved_by` → `core.admin_user`
- `portfolio.opening_balance_application.obligation_id` → `ledger.tax_obligation`
- `portfolio.opening_balance_application.account_movement_id` → `ledger.tax_account_movement`

## COLLECTION — Cobro persuasivo y coactivo (7 tablas)

```mermaid
erDiagram
    collection_collection_case {
        UUID id PK
        UUID administration_id FK
        UUID taxpayer_id FK
        VARCHAR_60 case_number
        VARCHAR_30 collection_stage
        VARCHAR_30 status
        DATE opened_at
        UUID assigned_officer_id FK
        DATE closed_at
        VARCHAR_40 closure_reason
    }
    collection_collection_case_obligation {
        UUID id PK
        UUID case_id FK
        UUID obligation_id FK
        DATE included_at
        VARCHAR_30 status
    }
    collection_collection_action {
        UUID id PK
        UUID case_id FK
        UUID legal_act_id FK
        VARCHAR_50 action_type
        DATE action_date
        VARCHAR_30 action_status
        TEXT observations
    }
    collection_enforcement_title {
        UUID id PK
        UUID case_id FK
        UUID obligation_id FK
        UUID source_document_id FK
        VARCHAR_40 title_type
        DATE enforceable_date
        NUMERIC_18_2 principal
        NUMERIC_18_2 penalties
        VARCHAR_30 verification_status
    }
    collection_payment_order {
        UUID id PK
        UUID case_id FK
        UUID legal_act_id FK
        VARCHAR_60 order_number
        DATE issue_date
        VARCHAR_30 status
    }
    collection_payment_facility {
        UUID id PK
        UUID case_id FK
        UUID approving_act_id FK
        DATE approval_date
        INTEGER installment_count
        NUMERIC_18_2 granted_amount
        VARCHAR_30 status
    }
    collection_facility_installment {
        UUID id PK
        UUID facility_id FK
        INTEGER installment_number
        DATE due_date
        NUMERIC_18_2 principal_amount
        NUMERIC_18_2 estimated_interest
        NUMERIC_18_2 paid_amount
        VARCHAR_30 status
    }
    collection_collection_case ||--o{ collection_collection_case_obligation : case_id
    collection_collection_case ||--o{ collection_collection_action : case_id
    collection_collection_case ||--o{ collection_enforcement_title : case_id
    collection_collection_case ||--o{ collection_payment_order : case_id
    collection_collection_case ||--o{ collection_payment_facility : case_id
    collection_payment_facility ||--o{ collection_facility_installment : facility_id
```

**Tablas y funciones:**

- `collection.collection_case` — Expediente persuasivo o coactivo abierto contra un deudor.
- `collection.collection_case_obligation` — Relación muchos-a-muchos expediente / obligaciones.
- `collection.collection_action` — Cronología de gestiones y actuaciones de cobranza.
- `collection.enforcement_title` — Identificación y validación del título ejecutivo por obligación.
- `collection.payment_order` — Mandamiento de pago del procedimiento coactivo.
- `collection.payment_facility` — Facilidades de pago otorgadas por acto competente.
- `collection.facility_installment` — Calendario de cuotas de una facilidad de pago.

**Relaciones externas:**

- `collection.collection_case.administration_id` → `core.tax_administration`
- `collection.collection_case.taxpayer_id` → `core.taxpayer`
- `collection.collection_case.assigned_officer_id` → `core.admin_user`
- `collection.collection_case_obligation.obligation_id` → `ledger.tax_obligation`
- `collection.collection_action.legal_act_id` → `documents.legal_act`
- `collection.enforcement_title.obligation_id` → `ledger.tax_obligation`
- `collection.enforcement_title.source_document_id` → `documents.document`
- `collection.payment_order.legal_act_id` → `documents.legal_act`
- `collection.payment_facility.approving_act_id` → `documents.legal_act`

## ENFORCEMENT — Bienes y medidas cautelares (6 tablas)

```mermaid
erDiagram
    enforcement_asset_investigation {
        UUID id PK
        UUID case_id FK
        VARCHAR_40 institution_type
        VARCHAR_200 requested_entity
        DATE request_date
        DATE response_date
        VARCHAR_30 result_status
        UUID document_id FK
    }
    enforcement_debtor_asset {
        UUID id PK
        UUID taxpayer_id FK
        VARCHAR_40 asset_type
        VARCHAR_100 asset_identifier
        TEXT description
        NUMERIC_18_2 estimated_value
        NUMERIC_7_4 ownership_pct
        DATE verified_at
        VARCHAR_30 status
    }
    enforcement_precautionary_measure {
        UUID id PK
        UUID case_id FK
        UUID debtor_asset_id FK
        UUID ordering_act_id FK
        VARCHAR_40 measure_type
        NUMERIC_18_2 ordered_limit
        DATE ordered_at
        VARCHAR_30 status
    }
    enforcement_measure_notification {
        UUID id PK
        UUID measure_id FK
        VARCHAR_200 recipient_entity
        VARCHAR_80 outgoing_reference
        DATE sent_at
        DATE acknowledged_at
        VARCHAR_30 response_status
        UUID response_document_id FK
    }
    enforcement_measure_execution {
        UUID id PK
        UUID measure_id FK
        NUMERIC_18_2 affected_amount
        NUMERIC_18_2 deposited_amount
        DATE execution_date
        VARCHAR_80 deposit_reference
        VARCHAR_30 status
    }
    enforcement_measure_release {
        UUID id PK
        UUID measure_id FK
        UUID legal_act_id FK
        DATE ordered_at
        DATE confirmed_at
        VARCHAR_40 release_reason
        VARCHAR_30 status
    }
    enforcement_debtor_asset ||--o{ enforcement_precautionary_measure : debtor_asset_id
    enforcement_precautionary_measure ||--o{ enforcement_measure_notification : measure_id
    enforcement_precautionary_measure ||--o{ enforcement_measure_execution : measure_id
    enforcement_precautionary_measure ||--o{ enforcement_measure_release : measure_id
```

**Tablas y funciones:**

- `enforcement.asset_investigation` — Solicitudes y respuestas para investigar bienes del deudor.
- `enforcement.debtor_asset` — Bienes o derechos patrimoniales identificados como del deudor.
- `enforcement.precautionary_measure` — Orden judicial o administrativa de medida cautelar en cobro.
- `enforcement.measure_notification` — Oficios de inscripción o comunicación a terceros para ejecutar medida.
- `enforcement.measure_execution` — Ejecución material y recursos retenidos por medida cautelar.
- `enforcement.measure_release` — Levantamientos totales o parciales de medidas cautelares.

**Relaciones externas:**

- `enforcement.asset_investigation.case_id` → `collection.collection_case`
- `enforcement.asset_investigation.document_id` → `documents.document`
- `enforcement.debtor_asset.taxpayer_id` → `core.taxpayer`
- `enforcement.precautionary_measure.case_id` → `collection.collection_case`
- `enforcement.precautionary_measure.ordering_act_id` → `documents.legal_act`
- `enforcement.measure_notification.response_document_id` → `documents.document`
- `enforcement.measure_release.legal_act_id` → `documents.legal_act`

## AUDIT — Auditoría e integraciones (3 tablas)

```mermaid
erDiagram
    audit_audit_event {
        UUID id PK
        UUID user_id FK
        UUID administration_id FK
        VARCHAR_80 entity_name
        UUID entity_id
        VARCHAR_40 operation
        JSONB previous_values
        JSONB new_values
        TIMESTAMPTZ occurred_at
        VARCHAR_80 correlation_id
        VARCHAR_30 source
        VARCHAR_100 source_event_id
        VARCHAR_200 actor_ref
    }
    audit_import_cursor {
        VARCHAR_30 source PK
        TIMESTAMPTZ last_event_at
        TEXT_ARRAY seen_event_ids
        BOOLEAN complete
        TIMESTAMPTZ updated_at
    }
    audit_external_integration_event {
        UUID id PK
        UUID administration_id FK
        VARCHAR_100 system_name
        VARCHAR_60 operation_type
        VARCHAR_100 external_reference
        TIMESTAMPTZ request_at
        TIMESTAMPTZ response_at
        VARCHAR_30 status
        VARCHAR_80 correlation_id
        VARCHAR_80 error_code
    }
```

**Tablas y funciones:**

- `audit.audit_event` — Bitácora inmutable de cambios administrativos y financieros. Feature 001:
  - `administration_id`: la administración del evento, para el aislamiento por RLS. Puede ser nula: los eventos de plataforma (como un inicio rechazado de alguien sin administración) y los importados del proveedor no la tienen, y la aplicación no los lee. Si hay `user_id`, la administración es obligatoria (CHECK), y la FK compuesta `(user_id, administration_id)` → `core.admin_user` impide mezclar administraciones;
  - `source` (`LIV` o `IDENTITY_PROVIDER`) y `source_event_id`, obligatorios y únicos juntos, para que un evento importado dos veces quede una sola vez;
  - `actor_ref`: quién actuó cuando no es un usuario de LIV (por ejemplo, el nombre intentado en un inicio fallido, enmascarado);
  - los eventos de sesión se guardan con `entity_name` = `session` o `identity_provider`, `operation` = el tipo de evento, y `new_values` = `{result, reason, truncated_ip, masked_attempted_username}`, sin contraseñas ni códigos (001-18).
- `audit.import_cursor` — Hasta dónde se importaron los eventos del proveedor de identidad a `audit.audit_event` (feature 001); `source` solo vale `IDENTITY_PROVIDER`: el último instante, los ids ya vistos en ese instante y si la última lectura fue completa.
- `audit.external_integration_event` — Trazabilidad de llamadas a fuentes, RUNT, bancos y pasarelas.

**Relaciones externas:**

- `audit.audit_event.user_id` → `core.admin_user`, con `administration_id` en una FK compuesta
- `audit.audit_event.administration_id` → `core.tax_administration`
- `audit.external_integration_event.administration_id` → `core.tax_administration`

## Esquemas técnicos (plataforma y BFF)

No son parte del catálogo funcional: los usa la plataforma, no el negocio. Los aprobó el responsable en H2 de la feature 001 (2026-11-01). Sus roles de base de datos están en `docs/arquitectura/02` §5.2.

```mermaid
erDiagram
    platform_app_role {
        VARCHAR_40 code PK
        VARCHAR_200 description
    }
    platform_app_permission {
        VARCHAR_80 code PK
        VARCHAR_200 description
    }
    platform_role_permission {
        VARCHAR_40 role_code PK
        VARCHAR_80 permission_code PK
    }
    bff_oidc_session {
        CHAR_36 session_id PK
        VARCHAR_2048 issuer
        VARCHAR_255 sid
        VARCHAR_255 subject
        BIGINT created_at
        BIGINT pending_end_since
    }
    platform_app_role ||--o{ platform_role_permission : role_code
    platform_app_permission ||--o{ platform_role_permission : permission_code
```

**Tablas:**

| Esquema | Tabla | Para qué |
|---|---|---|
| `platform` | `app_role`, `app_permission`, `role_permission` | Roles, permisos y qué permisos tiene cada rol (matriz en Doc. 01 §4.1). El rol de una persona llega del proveedor de identidad |
| `platform` | `event_publication` | Eventos entre módulos que aún no se procesan (Spring Modulith). No se dibuja: su estructura es la de Spring Modulith |
| `platform` | `shedlock` | Bloqueo de las tareas programadas de la API: con varias réplicas, una sola a la vez. No se dibuja: es la tabla estándar de ShedLock |
| `bff` | `spring_session`, `spring_session_attributes` | Sesiones del BFF en el servidor; los *tokens* van cifrados en un atributo |
| `bff` | `oidc_session` | Qué sesión del BFF es de qué sesión del proveedor (`sid`), para el cierre por canal trasero, y los avisos a LIV pendientes |
| `bff` | `shedlock` | Bloqueo del barrido de las sesiones vencidas del BFF |

## Reglas de implementación de apertura de cartera histórica

1. **Carga independiente:** `opening_balance_*` no puede reutilizar `valuation_import_*`; los archivos y procesos tienen distinta finalidad.
2. **Unidad de apertura:** cada fila identifica un sujeto, vehículo, administración, vigencia, concepto(s), saldos y fecha de corte; no se debe asumir que la placa identifica por sí sola al deudor histórico.
3. **Staging y conciliación:** las filas se cargan sin afectar el libro mayor. Comparar totales del archivo, subtotales por componente, duplicados y obligaciones preexistentes.
4. **Aprobación segregada:** el aprobador debe ser distinto del cargador; nunca publicar lotes con errores críticos o diferencias no explicadas.
5. **Asiento controlado:** publicar en una transacción que cree o vincule la obligación y movimientos `OPENING_BALANCE` respaldados por `opening_balance_application`.
6. **Idempotencia:** clave única de origen por administración, lote y fila; además impedir reaplicaciones de una obligación/componente por reinicios de importación.
7. **Intereses:** registrar el importe recibido, su fecha de corte y soporte; no capitalizarlo ni contabilizarlo dos veces al hacer futuros recálculos.
8. **Validez jurídica:** distinguir `RECEIVED`, `RECONCILED`, `POSTED`, `LEGALLY_REVIEWED` y `COLLECTIBLE`; migrar un saldo no acredita por sí solo título ejecutivo ni interrumpe prescripción.
9. **Archivo inmutable:** conservar fuente, SHA-256 y JSONB original; las correcciones requieren otro lote o movimientos reversos auditados.
10. **Extensiones para producción:** vincular `source_system`, identificador de obligación origen, comprobantes, actos de determinación, fechas de ejecutoria, notificaciones y mandamientos; agregar índice de conciliación por `(administration_id, fiscal_year)` y controles de tenant.

## Checklist de aceptación previo a Flyway

_Feature 001: en H2 (2026-11-01) el responsable autorizó migrar, antes de cerrar esta lista, las tablas de `platform`, `bff`, `core.territorial_entity`, `core.tax_administration`, `core.admin_user`, `core.user_session` y `audit`. Ninguna es tributaria. La lista sigue abierta para el resto del modelo._

- [ ] Confirmar que cada FK referencia registros de la misma entidad territorial donde corresponda.
- [ ] Congelar catálogos de avalúos publicados y registrar correcciones con versión distinta.
- [ ] Validar CSV UTF-8/BOM, separador, encabezados de años modelo, valor de miles/pesos, hash y duplicados.
- [ ] Comprobar staging, reporte de errores, aprobación por usuario diferente al cargador y publicación transaccional.
- [ ] Definir UNIQUE por (catalog_id, ministry_reference_code, model_year) y por (batch_id, source_row_number), ajustando las claves a las filas originales reales.
- [ ] Definir índices únicos parciales e intervalos sin solapamientos para matrículas, propietarios y tasas.
- [ ] Documentar reglas de redondeo, facturación y sanciones por año/jurisdicción.
- [ ] Formalizar controles de auditoría, estados jurídicos, documentos y permisos de funcionarios.
- [ ] Validar ERD con expertos tributarios de Bogotá antes de migraciones.
- [ ] Probar importación de cartera histórica, conciliación, rollback y publicación idempotente.
- [ ] Verificar prescripción y exigibilidad por obligación antes de habilitar cobro coactivo.

## Reglas de implementación de la carga CSV

1. Los CSV se reciben con delimitador configurable (`;` o `,`) y codificación declarada (preferiblemente UTF-8); el parser debe manejar comillas, campos vacíos y BOM.
2. Cada fila original se conserva íntegra en `valuation_import_row.original_data`. El parser genera una versión validada en `normalized_data`, que puede contener varios años modelo.
3. `source_row_number` es el número físico de la fila CSV, y la columna concreta debe permanecer vinculada a cada avalúo normalizado para auditoría.
4. `monetary_multiplier` vale 1000 si las cifras fuente están en miles de pesos y 1 si están en pesos. Unidades y separadores decimales se validan antes de convertir a `NUMERIC(18,2)`.
5. Los registros inválidos se documentan en `valuation_import_error`; no se publican parcialmente archivos con errores críticos.
6. `catalog_id` refiere a la versión que se pretende publicar; una corrección requiere otra versión, sin actualizar avalúos ya utilizados en liquidaciones.
7. El estado `PUBLISHED` solo se asigna tras una transacción completa y una comprobación de totales e integridad referencial.
8. La publicación debe ser idempotente por `file_sha256`, catálogo y versión. Debe existir separación de funciones entre cargador y aprobador.
9. El archivo fuente se conserva en almacenamiento documental con control de acceso; el hash SHA-256 identifica su contenido.
10. El CSV oficial no sustituye el PDF/resolución ministerial: la aprobación debe contrastar datos y soporte normativo.

## Observación

Las relaciones se derivan de las FK descritas en el catálogo; los enlaces de `model_year` a la celda del anexo requieren prueba real de importación. Este diagrama es lógico y no sustituye las migraciones SQL.