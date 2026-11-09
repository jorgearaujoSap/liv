# 005 · Cartera y recaudo: documentación funcional, configuración y esquema 1.1 — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** borrador — pendiente de la revisión adversarial y de la aprobación H2
_(borrador → aprobado (H2))_

## Enfoque

La feature lleva al repositorio documentación funcional, configuración y un contrato. No hay código de producción. Se reparte en **cinco PR de un solo proyecto** (`AGENTS.md` §4.6): cuatro de la raíz y uno del backend.

- **Los documentos se incorporan desde el insumo** de `.entrada-claude-ai/`, que se trata como dato, con las 14 decisiones de la H1 y las decisiones P1 a P15 de este plan ya aplicadas. Del Doc. 04 solo cambian las secciones que lista 005‑08: se edita la v0.1 del repositorio, no se copia la v0.2.
- **Los dos YAML se escriben una vez**, en el PR de los anexos (R3). El PR del backend (B1) copia los YAML ya revisados a sus recursos de prueba, como instantánea (IN‑01), y agrega el esquema 1.1.
- **El esquema 1.1 es el 1.0 más lo nuevo**: `1.1.json` empieza como una copia del `1.0.json` y le suma las claves nuevas. Así, lo que hay que revisar es `diff 1.0.json 1.1.json`. El 1.0 no cambia (005‑16), y el mecanismo de versiones, pruebas y publicación es el de la feature 004 (ADR 0032).
- **Orden:** primero se publica el contrato y después se migran los anexos (`docs/arquitectura/01` §5.3, tal como lo ajustó la feature 004). Por eso B1 se integra antes que R3, aunque B1 copie los YAML de R3 (P1).

## Implementación

Cada paso es un PR. Sus archivos son los únicos que puede tocar; los detalla `tasks.md`.

1. **R1 · Doc. 05 v0.1** (raíz, rama `feat/005-doc05`) — criterios 005‑01 a 005‑07.
   - Crea `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md` desde el insumo, con estos ajustes:
     - **Códigos** (decisión 1): `CH‑01`…`CH‑10`, `MO‑01`…`MO‑10`, `PS‑01`…`PS‑04` y `SA‑01`…`SA‑05`, también en las referencias cruzadas («ver IN‑08 b» pasa a «ver MO‑08 b»);
     - **tablas en inglés con los nombres del ERD 2.2** (decisión 2 y P10 a P13). En §1, §3.4, §6.2, §7.1 y §10, `collection_event` pasa a `portfolio.prescription_event`, `prescription_case` a `portfolio.prescription_evaluation` y `portfolio_snapshot` a `portfolio.collection_portfolio_snapshot`, más la cabecera `portfolio.portfolio_cutoff`. En §10, los campos se escriben en inglés, como en el ERD. La fila de `ledger.tax_obligation` ya no guarda la fecha de prescripción (P13);
     - **§5.1** dice que las reglas de sanción se leen de la configuración y se materializan en `tax.penalty_rule` al publicarla (P9);
     - **§9:** la matriz de roles queda como «Propuesta», con una nota que remite al pendiente (decisión 3);
     - **§4.3:** CA‑5.03 aclara que sus valores son los de un cálculo único, sin cortes intermedios (decisión 4), y se quita la referencia a `scripts/intereses_mora.py`;
     - **§6.3:** los ejemplos quedan marcados como ilustrativos (decisión 5);
     - **§0 y §4.1:** la UVT y las tasas de usura llevan «por verificar con la fuente oficial» (decisión 6 y parte B, puntos 1 y 2), y las fuentes de prensa siguen citadas con esa nota;
     - **§11:** a los 8 pendientes se suman los del punto 5 de la parte B y el de los roles: 13 en total, cada uno con su responsable;
     - **§14 nuevo, «Cambios aplicados a otros documentos»,** como el Doc. 03 §7 (P14).
   - Tamaño: unas 470 líneas, todas de un archivo nuevo. Se justifica en el PR (Q12): es un solo documento, transcrito de un insumo que la persona ya revisó, y partirlo dejaría un documento a medias en `main`.

2. **R2 · ERD 2.2** (raíz, rama `feat/005-erd`) — 005‑15.
   - Encabezado 2.2: de 65 a 78 tablas, y por qué (los Docs. 04 v0.2 y 05 v0.1).
   - Esquema `ledger`, de 7 a 19 tablas: los nueve ajustes del Doc. 04 §11, en el diagrama Mermaid, en la lista de tablas y en las relaciones externas.
     - Tablas nuevas: `payment_receipt`, `payment_receipt_line`, `bank_file`, `bank_file_row`, `bank_reconciliation`, `distribution_batch`, `participation_report`, `participation_report_row`, `participation_match`, `participation_claim`, `credit_balance_use` y el catálogo `movement_type` (P11).
     - Columnas nuevas en `tax_obligation`: `origin_modality`, `declared_address_municipality_id`, `discount_deadline`, `origin`, `final_assessment_date` y `enforceable_from` (estas tres últimas, del Doc. 05 §10).
     - Columnas nuevas en otras tablas: en `payment`, `receipt_id`, `channel` y `bank_file_row_id`; en `revenue_distribution`, `distribution_batch_id`, `component_type` y `days_to_transfer`; en `credit_balance`, `origin_type` y `origin_payment_id`.
     - La vista materializada `v_obligation_balance` va en una nota: no es una tabla.
   - Esquema `portfolio`, de 11 a 12 tablas: los ajustes del Doc. 05 §10 sobre las tablas que ya existen (decisión 2).
     - `interest_rate`: período, usura, puntos restados, resolución de la SFC, SHA‑256 del PDF, estado, cargador y aprobador; llave única por tipo y período (P10).
     - `interest_calculation`: motivo, desde, hasta, total con y sin redondeo, movimiento y reverso.
     - `interest_calculation_detail`: `interest_rate_id`.
     - `prescription_event`: evento que anula, soporte y número del acto.
     - `prescription_evaluation`: origen, estado del expediente, acto, decisión y movimiento `WRITE_OFF`.
     - `collection_portfolio_snapshot`: corte, sujeto, vehículo, vigencia, fecha de prescripción, semáforo y modalidad; particionada por fecha.
     - Tabla nueva: `portfolio_cutoff` (P12).
   - Las reglas de cartera histórica y los hallazgos 6, 7 y 11 no cambian. Unas 300 líneas.

3. **R3 · Doc. 04 v0.2, Doc. 03 v0.5 y anexos** (raíz, rama `feat/005-recaudo-config`) — 005‑08 a 005‑14, CJ15 y CJ16.
   - `04_Cuenta_Corriente_y_Recaudo.md`, sobre la v0.1:
     - las secciones que lista 005‑08;
     - un §12 nuevo («Canales de recaudo»): §12.1 sin la fila de la pasarela; §12.2, que remite al anexo; §12.3 con la nota de la pasarela (005‑09); §12.4 y §12.5;
     - las notas de PSE en §4.1 y §6.2 (decisión 11);
     - la nota del ejemplo 2 de §5.2 (parte B, punto 7) y §15, que remite al Doc. 05.
   - `03_Configuracion_Jurisdiccion_y_Tablas_2026.md`, a la v0.5:
     - §5.2, con las filas `recaudo` y `cartera`;
     - §5.4, con CJ15 y CJ16 tal como los fija la spec, y la nota de CJ02 sobre `prescripcion.competente` (decisión 7);
     - §7, con las filas de la v0.5 (P14);
     - §9.2, con el resultado de Ipiales con CJ01 a CJ16. Se evalúa a mano, regla por regla, y el PR lo muestra en una tabla.
   - Los dos YAML: `esquema_version: "1.1"`, las cuatro normas, `recaudo` (P4 y P6), `cartera` (P3, P5, P7 y P8) y los estados de la decisión 6. La plantilla lleva los valores que da el `LEEME.md` del insumo, y `sistema_determinacion: PENDIENTE`.
   - `docs/funcional/anexos/Formatos_Intercambio_Recaudo.xlsx`: copia binaria del insumo. Antes de subirla, se comprueban su tamaño (13.779 bytes) y su SHA‑256 (`27023a14…0ff90c`), y se inspecciona con `openpyxl`, como dato y en modo de solo lectura: sin vínculos externos ni macros, y con datos ficticios (`AGENTS.md` §4.5).
   - Unas 350 líneas de texto, más el binario.

4. **B1 · Esquema 1.1** (`backend/`, rama `feat/005-esquema-1-1`) — CJ01, 005‑16 a 005‑19. Usa la *skill* de código que corresponda y el patrón de la feature 004.
   - **Rojo** (*commit* `test:`):
     - las pruebas de esquema corren por versión, y se agregan los casos de la 1.1;
     - «CJ01 · la plantilla y la configuración de Ipiales 1.1 son válidas»;
     - «005‑18 · un YAML válido en 1.0, con la versión cambiada, es válido en 1.1», sobre las copias de los anexos 1.0 y sobre los casos válidos de `1.0.casos.json`;
     - las mutaciones de las claves nuevas (clave desconocida, `estado` fuera de lista, listas cerradas, `PENDIENTE` donde vale y donde no, fracciones), y la tabla de clases de campo de la 1.1.
     - La meta‑prueba `JsonSchemaFilesTest` ya exige que cada esquema tenga sus casos y cumpla 004‑11, así que falla si falta `1.1.casos.json`.
   - **Verde** (*commit* `feat:`):
     - `contracts/jsonschema/config-jurisdiccion/1.1.json`: copia del 1.0, con `$id` y `const` en 1.1, `archivo_banco.formato` opcional y las claves nuevas;
     - `1.1.casos.json`: los casos de carga del 1.0 sin cambio y los de validación con `"1.1"`, más los casos nuevos;
     - las copias de los anexos: las de la 1.0 se mueven con `git mv` a `src/test/resources/…/config-jurisdiccion/1.0/`, para no debilitar ninguna prueba de la 1.0, y las de la 1.1 van en `…/1.1/`, idénticas a las de R3;
     - una entrada en `CHANGELOG.md`.
   - **Tamaño:** unas 4.500 líneas, de las que casi todo es copia: `1.1.json` y `1.1.casos.json` copian el 1.0, y las copias de los anexos son idénticas a las de R3. El cambio real son unas 500 líneas, y el PR da los comandos para verlo (`git diff --no-index` del 1.0 contra el 1.1, y `diff` de las copias contra R3). Partirlo no sirve: integrar en `main` publica la versión y la congela (004‑12).
   - **Al integrar,** `backend-main` publica la *release* `jsonschema-config-jurisdiccion-v1.1` (005‑19).

5. **R4 · Reglas y referencias** (raíz, rama `feat/005-referencias`) — 005‑20 y 005‑21.
   - `AGENTS.md`: §1.1, §3.1 (Doc. 05 en el orden de lectura) y §5.3 (prefijos y ejemplos).
   - `README.md`, `spec/constitution/tech-stack.md` y el árbol de `docs/arquitectura/01` §14: «documentos 01–05».
   - Doc. 01 §3 (menú 5 con su opción 5.6, remitiendo al Doc. 05) y Doc. 02 §13 (remite al Doc. 05). El Doc. 01 sube a la v0.4; el Doc. 02 no sube de versión, porque solo cambia una referencia.
   - Unas 40 líneas.

6. **Cierre (orquestador):** el *roadmap* (Fase 5 con sus features, Fase 6 todavía sin documento funcional; 005‑22), el glosario y `ESTADO.md` (`AGENTS.md` §7).

## Datos, API y seguridad

- **Migraciones:** ninguna. El ERD 2.2 es modelo lógico; las tablas se crean con las features de las Fases 4 y 5.
- **Contrato:** el esquema JSON de la configuración pasa a 1.1 y es **compatible** con el 1.0 (decisión 10, 005‑18). El 1.0 sigue publicado y no cambia. No hay cambios en OpenAPI.
- **Permisos:** no cambia ningún rol, permiso, *realm* ni catálogo del menú (005‑06). La matriz del Doc. 05 §9 queda como propuesta.
- **Amenazas** (`docs/arquitectura/02` §2):
  - **Contenido externo** (`AGENTS.md` §4.4, regla 7): el insumo, el Excel y las fuentes de prensa son datos. El Excel se comprueba por tamaño y SHA‑256 y se inspecciona antes de subirlo (paso 3).
  - **Datos personales:** los ejemplos del Excel y de los documentos tienen que ser ficticios. Si el Excel trae una placa o un documento que parezca real, se detiene el PR y se pregunta.
  - **Integridad del contrato:** la publicación de la 1.1 sigue el protocolo del ADR 0027 que adoptó el ADR 0032, y la comprobación de inmutabilidad protege la 1.0.

## Dependencias y recursos

- **Depende de:** nada que no esté integrado. La feature 004 (esquema 1.0 y su CI) ya está en `main`.
- **Recursos compartidos:**
  - el contrato `config-jurisdiccion`: solo lo toca B1;
  - los YAML de `docs/funcional/anexos/`: solo los toca R3, y B1 los copia;
  - los archivos de *build* y el `CHANGELOG.md` del backend: B1. F0‑11 (pruebas *golden*) puede correr a la vez en `backend/`, porque no toca los mismos archivos, salvo el `CHANGELOG.md`, que se comparte;
  - ningún archivo lo tocan dos PR: el Doc. 04, el Doc. 03 y los anexos son de R3; el ERD es de R2; el Doc. 05 es de R1; `AGENTS.md`, `README.md`, `tech-stack.md`, `docs/arquitectura/01` y los Docs. 01 y 02 son de R4.
- **Orden:**
  - R1, R2 y R3 se pueden escribir y revisar en paralelo: los nombres que comparten (códigos, tablas y claves) los fija este plan;
  - B1 empieza cuando la revisión de R3 aprobó el contenido de los YAML, y copia esos YAML;
  - **B1 se integra antes que R3** (P1);
  - R4 se integra después de R1, porque cita su archivo y sus códigos;
  - conviene integrar R2 antes que R1, para que el Doc. 05 no cite tablas que el ERD todavía no tiene, aunque no es obligatorio;
  - el cierre del orquestador va al final.
  - En resumen: `R2 → R1 → R4`, en paralelo con `R3 (revisado) → B1 (integrado) → R3 (integrado)`, y después el cierre.

## Decisiones

- **P1 · Cinco PR y el contrato antes que los anexos** — `AGENTS.md` §4.6 pide un proyecto por PR y «primero el backend con el contrato», y la feature 004 fijó que una versión del esquema se publica antes de migrar los anexos. Si R3 se integra antes, los anexos declararían una versión que todavía no existe.
  - **Riesgo:** que R3 cambie después de que B1 copió los YAML.
  - **Control:** R3 no admite cambios en sus YAML después de que B1 los copia; si un cambio hace falta, B1 vuelve a copiar. Al integrar R3, su descripción muestra que `diff` entre los anexos y las copias de B1 da vacío.
  - **Descartado:** un PR con los dos proyectos (rompe §4.6), y escribir los YAML primero en el backend (los anexos son la fuente; las copias, instantáneas).
- **P2 · El 1.1 como copia del 1.0 más lo nuevo, y compatible** — cada versión es un archivo completo e inmutable (ADR 0032), y la copia hace que la revisión sea un `diff`.
  - Las claves nuevas dentro de objetos que ya existen son opcionales, y `archivo_banco.formato` pasa de obligatorio a opcional (decisión 10). Dentro de los objetos nuevos (`cartera` y sus partes, `reporte_participacion_20`, `bancos[]`) rige la regla general del 1.0: obligatoria la clave que traen los dos YAML.
  - **Descartado:** un esquema 1.1 que importe el 1.0 con `$ref` externo, que el 1.0 prohíbe (subconjunto portable).
- **P3 · `sistema_determinacion` en `cartera.sanciones`, con `LIQUIDACION_FACTURA`, `DECLARACION` o `PENDIENTE`** (decisión 9).
  - **Dónde va:** junto a las reglas que lo usan (`aplica_si`). Así el cambio queda en la sección nueva y no toca `causacion` ni `jurisdiccion`.
  - **Excepción a 004‑08** («`PENDIENTE` nunca en listas cerradas»), con aprobación explícita en la H2: la plantilla lo necesita (decisión 9), y CJ16 trata `PENDIENTE` como «no es `DECLARACION`».
  - **Descartado:** dejarlo fuera de la plantilla, porque las dos tienen que tener las mismas claves (005‑11).
- **P4 · `archivo_banco.formato_por_banco` como lista** `[{banco, formato}]`, vacía al principio, en lugar del mapa `{}` del insumo. Un mapa con claves libres (los códigos de banco) no admite `additionalProperties: false`, que la meta‑prueba exige en todo objeto (004‑02).
  - **Descartado:** abrir el mapa, que permitiría erratas en silencio, y llevar el formato a cada `bancos[]`, que cambia más la forma del insumo.
- **P5 · Se quita `cartera.intereses_mora.tabla_tasas`** (`portfolio.interest_rate` en el insumo). La configuración describe reglas de la jurisdicción, no dónde las guarda el sistema: con esa clave, un cambio de tabla obligaría a una versión nueva del esquema. Que las tasas vienen de la tabla mensual lo dicen el Doc. 05 §4.1 y `tasa_referencia`.
- **P6 · Se mantiene `recaudo.estado_bancos`** como clave opcional con `$defs/estado`. Dice que la lista de bancos está `VIGENTE` aunque `recaudo` siga `POR_CONFIRMAR`, y quitarla perdería ese dato del insumo.
- **P7 · Dos clases nuevas que admiten `PENDIENTE`,** las dos con `anyOf` y los `$defs` que ya existen, sin palabras clave nuevas (004‑11):
  - `anio_o_pendiente`: entero ≥ 0 o `PENDIENTE`, para `cartera_historica.vigencias_desde`;
  - `norma_o_pendiente`: código o `PENDIENTE`, para `cartera.sanciones.estatuto_municipal`, que cuando se diligencie será una norma de `normas` (CJ03).
  - Son los únicos campos nuevos con `PENDIENTE` aparte de los que ya lo admiten: textos libres (`cuenta_recaudo`, `competente`) y fechas (`fecha_corte_migracion`).
- **P8 · Listas cerradas nuevas, solo donde el insumo documenta todas las opciones** (004‑05):
  - `prescripcion.inicio_termino`: `FECHA_EJECUTORIA_FACTURA` o `FECHA_LIMITE_PAGO`;
  - `cartera_historica.intereses_historicos`: `RECALCULAR` o `TOMAR_DEL_LEGADO`;
  - `sanciones.sistema_determinacion` (P3);
  - `intereses_mora.redondeo_causacion.metodo`, con la misma lista de `redondeo.metodo`, que incluye `SIN_REDONDEO` (Doc. 05 §4.3).
  - El resto son códigos abiertos (`formula`, `dias_anio`, `tramos`, `inicio`, `declaracion`, `reinicio`, `aplica_si`, los eventos, `canales`, los formatos), y las referencias a normas también son códigos.
  - Clases de los valores numéricos: tasas como fracción (`puntos_a_restar`, `tarifa_mes_o_fraccion`, `tope_sobre_impuesto`); montos en pesos (`uvt.valor`, `tolerancia_conciliacion_pesos`, `sancion_minima_uvt` como número ≥ 0); enteros (`anios`, `uvt.vigencia`, `semaforo_dias.*`, `rangos_dias[]`, `multiplo`).
- **P9 · Las reglas de sanción salen de la configuración.** Al publicarla, la sección `cartera.sanciones` se materializa en `tax.penalty_rule`, como las tarifas se materializan en `tax.tax_rate` (Doc. 03 §5.2). El Doc. 05 §5.1 lo dice así. El Doc. 01 §6.7 (opción 1.5.8) no cambia aquí: cómo conviven el formulario de 1.5.5 a 1.5.8 y la configuración es la misma pregunta que ya existe para las tarifas, y la responde la feature de la opción 1.5.9.
- **P10 · `portfolio.interest_rate` es nacional, sin `administration_id`,** como `valuation.valuation_catalog`: la tasa de usura la certifica la Superfinanciera para todo el país, y cada administración la leería igual.
  - Quién la carga y la aprueba en un producto multi‑inquilino es la misma pregunta que tiene el catálogo de avalúos, y la decide la feature del Módulo 5.
  - **Descartado:** una copia por administración, que repite un dato nacional y puede divergir.
- **P11 · El catálogo de tipos de movimiento es `ledger.movement_type`.** El esquema `ref` del Doc. 04 §11 no existe, y el ADR 0003 pone un módulo en un esquema.
- **P12 · El corte mensual tiene cabecera:** `portfolio.portfolio_cutoff`, con administración, fecha, estado (`GENERADO`, `ERROR` o `PUBLICADO`), totales de control, el corte al que reemplaza y quién lo generó y lo publicó. El Doc. 05 §7.1 le da estado y reemplazo al corte, y eso no cabe en las filas por obligación. Es la única tabla nueva del Doc. 05.
- **P13 · La fecha de prescripción no se guarda en `ledger.tax_obligation`.** Se deriva de los eventos, y el ERD ya la tiene en `portfolio.prescription_evaluation.calculated_deadline`. Guardarla en la obligación daría dos fuentes de verdad para el mismo dato (`AGENTS.md` §5.1: lo derivable no se guarda). Las fechas de ejecutoria y de exigibilidad sí se guardan, porque son hechos.
- **P14 · Registro de los cambios entre documentos** (`AGENTS.md` §3.1):
  - el Doc. 05 tiene un §14 nuevo, «Cambios aplicados a otros documentos», con los cambios en los Docs. 01 §3, 02 §13, 03 §5.2 y §5.4, 04 §12 y §15 y el ERD;
  - el Doc. 03 §7 agrega las filas de su v0.5.
- **P15 · Sin ADR nuevo.** No hay dependencias, módulos ni dependencias entre módulos nuevas, y el esquema 1.1 sigue el mecanismo del ADR 0032. P10 y P11 son decisiones de modelo de datos, y quedan en el ERD.

## Riesgos

- **Errores al pasar el Doc. 05 al repositorio** (códigos, nombres de tablas, ejemplos):
  - la revisión de R1 lo compara con el insumo, verificado por SHA‑256, y aplica las verificaciones de abajo;
  - la revisión adversarial de lo implementado repite el recálculo de §4.3 (005‑04).
- **Quedan códigos viejos.** Verificación:
  - en R1 y R4, ningún `IN‑`, `PR‑` ni `OB‑06` a `OB‑10` en el Doc. 05;
  - en todo `docs/`, `OB‑01` a `OB‑05` solo con el significado del Doc. 04 e `IN‑xx` solo con el de `docs/arquitectura/01`.
- **Plantilla e Ipiales se separan.** En R3, una comprobación de que las claves de las secciones nuevas son las mismas en los dos YAML, y el resultado va en el PR. En B1, la mutación y la tabla de campos fallan si una clave falta en uno de los dos.
- **Los anexos de R3 y las copias de B1 no coinciden** — P1: `diff` vacío al integrar R3.
- **PR grandes** (R1 y B1): la justificación de Q12 en cada uno y los comandos de `diff` para ver solo el cambio real.
- **El Excel trae datos reales o vínculos externos** — inspección antes de subirlo; si aparece algo así, se detiene el PR y se pregunta (paso 3).
- **El resultado de §9.2 se evalúa a mano** (CJ01 a CJ16), mientras no exista el validador. Se muestra regla por regla en el PR de R3, y la feature de la opción 1.5.9 lo confirmará con código.
- **Ventana sin validar:** hasta la feature de la opción 1.5.9, nada valida los anexos en la raíz (004‑13). Las copias de B1 son la prueba de CJ01, y P1 evita que difieran.

## Verificaciones por PR

| PR | Verificación | Criterios |
|---|---|---|
| R1 | Recálculo de §4.3 con aritmética exacta; búsqueda de códigos viejos; cada tabla citada existe en el ERD 2.2 (rama de R2 o `main`); 13 pendientes en §11 con su responsable | 005‑01 a 005‑07 |
| R2 | Cada ajuste del Doc. 04 §11 y del Doc. 05 §10 aparece en el Mermaid, en la lista y en las relaciones; el número de tablas cuadra (78) | 005‑15 |
| R3 | `diff` del Doc. 04 contra la v0.1: solo las secciones de 005‑08; sin «Wompi», «WP‑», «PASARELA» ni «CA‑4.1[4-7]» salvo en la nota de reserva; SHA‑256 del Excel; claves iguales en las secciones nuevas de los dos YAML; tabla de CJ01 a CJ16 para Ipiales | 005‑08 a 005‑14, CJ15, CJ16 |
| B1 | `./mvnw verify` en verde (pruebas de esquema, mutación, meta‑prueba y `jsonschema.sh check`); `diff` de las copias contra R3 | CJ01, 005‑16 a 005‑18 |
| B1 (después de integrar) | La *release* `jsonschema-config-jurisdiccion-v1.1` existe, con sus SHA‑256 | 005‑19 |
| R4 | Ninguna referencia a «01–04» ni a «un futuro Módulo 5»; los prefijos de §5.3 existen en los documentos | 005‑20, 005‑21 |

Cada PR de la raíz lleva la lente de legibilidad antes del *commit*. Como el cambio es crítico, lo implementado pasa por la revisión adversarial y las cuatro lentes de `liv-revision-4r` antes de cada PR (`AGENTS.md` §4.3).

## Aprobaciones que pide la persona

- **H2:** este plan, en especial P1 (orden de integración) y las desviaciones del insumo: P3 (`PENDIENTE` en una lista cerrada, excepción a 004‑08), P4 (`formato_por_banco` como lista), P5 (se quita `tabla_tasas`) y P13 (la fecha de prescripción no va en la obligación).
- **H3 de R4:** el cambio de reglas de `AGENTS.md` (§3.1 y §5.3) y del árbol de `docs/arquitectura/01` §14, que no van por el flujo directo.
- **No hace falta la aprobación de §4.5 para un cambio incompatible:** el 1.1 es compatible con el 1.0 (005‑18). Agregar una versión del contrato sí es parte de esta H2.
- La integración de cada PR la hace la persona; la *release* 1.1 la publica `backend-main` al integrar B1.

## Revisión

- [ ] Revisión adversarial independiente del plan ejecutada; hallazgos resueltos o justificados.
- [ ] Aprobación H2: <quién, fecha>.
