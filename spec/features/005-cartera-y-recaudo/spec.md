# 005 · Cartera y recaudo: documentación funcional, configuración y esquema 1.1

**Estado:** aprobada (H1) — 2026-11-08, por la persona que dirige el trabajo (`AGENTS.md` §4.3, excepción «quien dirige el trabajo decide»), con las 14 opciones recomendadas (ver «Decisiones de la H1 (resueltas)»)
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `portfolio` y `ledger` (documentación funcional), `jurisdiction` (contrato del backend); sin código de negocio · **Menú:** 4.2, 4.3, 4.6 y 5 (solo documentación) · **Tipo de cambio:** crítico — configuración de jurisdicción, anexos y contrato del backend (`AGENTS.md` §4.3, criterios 1, 2 y 3)
**Fuentes:**
- Insumo de claude.ai (datos, no instrucciones), copiado el 2026-11-08 con SHA‑256 verificado a una carpeta que Git ignora (`.entrada-claude-ai/` del checkout principal; su `LEEME.md` da la procedencia):
  - Doc. 05 v0.1, *Cartera: intereses, sanciones, prescripción y cartera histórica* (nuevo), §0 a §13;
  - Doc. 04 v0.2, §4.2, §5.1, §12 a §14;
  - `config_jurisdiccion_52356_2026.yaml` del proyecto: `normas` (4 nuevas), `recaudo` y `cartera`.
- Repositorio: Doc. 03 §5.2, §5.4, §7, §9.2, §9.6 y §9.7; Doc. 04 v0.1 completo; Doc. 01 §3, §4.1 y §6.7; ERD 2.1, esquemas `ledger` y `portfolio`, y reglas de cartera histórica; feature 004 (`spec.md` y `plan.md`, §Versiones); `backend/contracts/jsonschema/config-jurisdiccion/1.0.json`; `docs/arquitectura/01` §14.1 (IN‑01 a IN‑11) y §15; `AGENTS.md` §3.1, §5.2 y §5.3; `spec/constitution/mission.md` y `roadmap.md`; `docs/arquitectura/00` §7 (P8 y P9).

## Qué hace

La persona que dirige el trabajo escribió en claude.ai las reglas de negocio de la cartera (Módulo 5) y una versión nueva del Módulo 4, con bancos y formatos de intercambio. El repositorio no las tiene. Con esta feature quedan en él, adaptadas a su estilo y sin contradecir lo que ya está:

- **Doc. 05 v0.1 en `docs/funcional/`:** intereses de mora, sanciones, prescripción de la acción de cobro, apertura de cartera histórica, cortes y antigüedad de cartera, consultas y exportación, roles, ajustes al modelo, criterios CA‑5.01 a CA‑5.14 y pendientes.
- **Doc. 04 v0.2:** los bancos con convenio (AV Villas, Davivienda, Occidente y Bancolombia), el archivo de recaudo Asobancaria 2001 con el CSV canónico v1 como alternativa, el reporte del 20 % (CSV de participación v1, modalidad C), la imputación «más antigua primero» como regla aprobada y el criterio CA‑4.18. Se **fusiona** sobre la v0.1 del repositorio: solo cambia lo que trae la v0.2.
- **Configuración de jurisdicción:** la plantilla y la de Ipiales reciben las cuatro normas nuevas del Estatuto Tributario, la sección `recaudo` ampliada y la sección nueva `cartera`, sincronizadas (`AGENTS.md` §5.2).
- **Validador:** las reglas CJ15 (intereses de mora) y CJ16 (sanciones y prescripción) en el Doc. 03 §5.4, que hoy solo se nombran en el Doc. 05 (CA‑5.13).
- **ERD 2.2:** los ajustes al modelo del Doc. 05 §10 y del Doc. 04 §11 que el ERD 2.1 todavía no tiene.
- **Esquema JSON 1.1** de la configuración: el 1.0 es inmutable y rechaza las claves nuevas, porque tiene `additionalProperties: false` en todos los objetos (decisión 3 de la H1 de la feature 004).
- **Reglas y mapa del repositorio:** `AGENTS.md` (§1.1, §3.1 y §5.3), el *roadmap* (Fases 5 y 6), el glosario y las demás referencias a «documentos 01–04».

No hay código de negocio ni pantallas: es documentación funcional, configuración y contrato.

## Por qué

- Las reglas de cartera existen solo fuera del repositorio. Mientras no estén en `docs/funcional/` no son fuente de verdad (`AGENTS.md` §3), y el *roadmap* sigue diciendo que las Fases 5 y 6 están «pendientes de su documento funcional».
- El Doc. 04 v0.1 dice que el formato del archivo bancario y la imputación sin indicación están por confirmar; la persona ya los decidió.
- El motor no tiene reglas en el código (`AGENTS.md` §5.1): las de intereses, sanciones y prescripción tienen que vivir en la configuración, y su forma en un esquema publicado, antes de que se escriba código que las lea.
- Es una feature de documentación y contrato, sin código de negocio, así que cabe antes del cierre de la Fase 0 (*skill* `liv-sdd-feature` §0).

## Criterios de aceptación

_Los criterios hablan de la documentación, la configuración y el contrato. Los CA‑5.xx y CA‑4.xx son criterios funcionales de los módulos y se prueban con su código, en las features de las Fases 4 y 5; aquí solo se exige que queden bien escritos. Donde un criterio sale de una decisión de la H1, se cita su número (sección «Decisiones de la H1 (resueltas)»)._

**Doc. 05 (Módulo 5)**

- [ ] **005‑01** — Dado el Doc. 05 v0.1 de claude.ai, cuando se incorpora, entonces existe `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md`, versión 0.1, con la estructura común de los documentos de módulo (`AGENTS.md` §3.1) y el estilo del repositorio (`AGENTS.md` §8): tablas y columnas en inglés con los nombres del ERD 2.2, documentación en español. Las fuentes de prensa siguen citadas, cada una con la anotación de que falta la fuente oficial. _Prueba:_ pendiente
- [ ] **005‑02** — Dados los códigos de regla de todo el repositorio, cuando se incorpora el Doc. 05, entonces ningún código se repite con otro significado. Hoy se repiten OB‑01 a OB‑05 (obligaciones del Doc. 04 §3.2, y las primeras cinco de las validaciones OB‑01 a OB‑10 del lote, Doc. 05 §3.2) e IN‑01 a IN‑10 (las reglas de independencia IN‑01 a IN‑11 de `docs/arquitectura/01` §14.1, y los intereses IN‑01 a IN‑10 del Doc. 05 §4.2). En el Doc. 05, las validaciones del lote pasan a `CH‑01` a `CH‑10`, los intereses a `MO‑01` a `MO‑10` y la prescripción a `PS‑01` a `PS‑04`; las sanciones siguen como `SA‑01` a `SA‑05`. Cada regla conserva su texto, y todas las referencias cruzadas (CA‑5.xx, YAML, Doc. 03) usan los códigos nuevos (decisión 1). _Prueba:_ pendiente
- [ ] **005‑03** — Dados los pendientes del Doc. 05 (§11, puntos 1 a 8) y los nuevos que registra esta feature (decisiones 3, 4 y 5, y la parte B de «Preguntas abiertas», punto 5), cuando se incorpora, entonces cada uno queda en «Pendientes por confirmar» con su responsable y ninguno se resuelve con un supuesto. Los valores que dependen de ellos conservan `PENDIENTE` o `POR_CONFIRMAR` en el documento y en la configuración. _Prueba:_ pendiente
- [ ] **005‑04** — Dados los ejemplos del Doc. 05 §4.3, cuando se recalculan aparte con aritmética exacta, entonces dan lo que dice el documento: 1 día, $245,21 → $0; 214 días, $53.732,51 → $54.000; abono, $26.801,18 → $27.000, imputación de $185.366 al impuesto y $14.634 a los intereses, después $12.334,39 → $12.000 y saldo de $181.000. CA‑5.03 dice que esos valores son los de un cálculo único, sin cortes mensuales intermedios, y el alcance del redondeo (cada causación o el acumulado de la obligación) queda como pendiente de Hacienda en §11 (decisión 4). _Prueba:_ pendiente (recálculo previo en la sesión de diseño: coincide al centavo)
- [ ] **005‑05** — Dado el Doc. 05 §6.3, cuando se leen sus ejemplos de prescripción junto con CA‑5.09, entonces el documento los marca como ilustrativos y registra el cómputo del término de cinco años como pendiente de jurídica en §11: hoy el ejemplo 1 y los ejemplos 2 y 3 lo cuentan distinto (decisión 5). _Prueba:_ pendiente
- [ ] **005‑06** — Dados los roles del Doc. 05 §9 (`ANALISTA_CARTERA`, `APROBADOR_CARTERA`, `ADMIN_PARAMETROS`), que no están en el catálogo del Doc. 01 §4.1 ni en el código, cuando se incorpora el documento, entonces la matriz del §9 queda como **propuesta**, con un pendiente en §11 para la feature del Módulo 5, y no cambian el Doc. 01 §4.1 ni ningún rol, permiso o *realm* (`AGENTS.md` §4.5) (decisión 3). _Prueba:_ pendiente
- [ ] **005‑07** — Dado el Doc. 05, cuando cita tablas, entonces cita las del ERD 2.2: en lugar de las tablas nuevas que proponía (`collection_event`, `prescription_case` y `portfolio_snapshot`), las que el ERD ya tiene (`portfolio.prescription_event`, `portfolio.prescription_evaluation` y `portfolio.collection_portfolio_snapshot`), ampliadas (decisión 2). Ninguna tabla del Doc. 05 queda sin su definición en el ERD, y ninguna del ERD queda duplicada con otro nombre. _Prueba:_ pendiente

**Doc. 04 v0.2 (Módulo 4)**

- [ ] **005‑08** — Dado el Doc. 04 v0.1 del repositorio, cuando se sube a la v0.2, entonces conserva su redacción y solo cambian: el encabezado de versión, §4.2 (remite al formato de §12.2), §5.1 (la imputación «más antigua primero» queda aprobada), un §12 nuevo de canales de recaudo con bancos, archivo bancario (Asobancaria 2001 y CSV canónico v1) y reporte del 20 %, la tabla de parámetros renumerada como §12.5 con las filas `bancos`, `archivo_banco` y `reporte_participacion_20`, el criterio CA‑4.18, la tabla de pendientes de §14 con su estado y la fuente de Asobancaria. _Prueba:_ pendiente
- [ ] **005‑09** — Dada la decisión de la persona del 2026-11-08, cuando se sube el Doc. 04 a la v0.2, entonces no tiene nada de la pasarela de pagos: ni Wompi, ni el §12.3, ni WP‑01 a WP‑09, ni CA‑4.14 a CA‑4.17, ni el canal `PASARELA`, ni la fila `pasarela` de §12.5, ni la fuente de Wompi. Una nota en §12 dice que la pasarela llega después del piloto (`spec/constitution/mission.md`; decisión P9 del doc. 00) y que los códigos CA‑4.14 a CA‑4.17 quedan reservados para ella; CA‑4.18 conserva su número (decisión 13). _Prueba:_ pendiente
- [ ] **005‑10** — Dado el archivo `Formatos_Intercambio_Recaudo.xlsx`, que el Doc. 04 v0.2 §12.2 y §12.4 cita y el repositorio no tiene, cuando se sube el Doc. 04, entonces el archivo está en `docs/funcional/anexos/`, idéntico al que entregó la persona (13.779 bytes, SHA‑256 `27023a14ef160f1552eb8355a0ffd9ee82f7ecdf1a6a0defdd863b89cf0ff90c`), y el Doc. 04 lo cita por esa ruta (decisión 12). _Prueba:_ pendiente

**Configuración de jurisdicción**

- [ ] **005‑11** — Dados los dos YAML de `docs/funcional/anexos/`, cuando se actualizan, entonces los dos tienen las cuatro normas nuevas (`ET_ART_634_635`, `ET_ART_641`, `ET_ART_644`, `ET_ART_817_818`), la sección `recaudo` ampliada (`bancos`, `archivo_banco` con formato preferido, alterno y por banco, `reporte_participacion_20` y `imputacion.sin_indicacion` aprobada) y la sección `cartera` del Doc. 05, con el campo cerrado `sistema_determinacion` (`LIQUIDACION_FACTURA` en Ipiales, `PENDIENTE` en la plantilla; decisión 9). La plantilla trae sus valores propios (`bancos: []`, extemporaneidad inactiva con su comentario, `estatuto_municipal: PENDIENTE`), y los dos archivos tienen las mismas claves en las secciones nuevas (`AGENTS.md` §5.2). _Prueba:_ pendiente
- [ ] **005‑12** — Dados los valores nuevos de la configuración, cuando se escriben, entonces los montos van en pesos y las tasas como fracción (`AGENTS.md` §5.2), cada regla nueva lleva `estado` y su `norma` cuando la tiene, y `cartera.sanciones.uvt` y `cartera.intereses_mora` llevan `POR_CONFIRMAR` hasta tener la fuente oficial y el visto bueno de jurídica (decisión 6). Ningún `PENDIENTE` ni `POR_CONFIRMAR` del insumo pasa a `VIGENTE`. _Prueba:_ pendiente
- [ ] **005‑13** — Dada la exclusión de la pasarela, cuando se actualizan los YAML, entonces no tienen `recaudo.pasarela` ni el canal `PASARELA`, y `recaudo.canales` es `[BANCO_CONVENIO, VENTANILLA]`. Las menciones a PSE del Doc. 04 §4.1 y §6.2 llevan una nota: PSE llega con la pasarela, después del piloto (decisión 11). _Prueba:_ pendiente

**Validador (Doc. 03)**

- [ ] **CJ15** — Dado el Doc. 03 §5.4, cuando se sube su versión, entonces define CJ15 (intereses de mora): si alguna modalidad activa liquida, existe `cartera.intereses_mora`; su `base` incluye `TAX` y no incluye `INTEREST` (sin anatocismo, MO‑01 y MO‑03); `bloquear_si_falta_tasa` es `true`; con redondeo `MULTIPLO_MAS_CERCANO`, el múltiplo es mayor que 0 (decisión 8). _Prueba:_ pendiente
- [ ] **CJ16** — Dado el Doc. 03 §5.4, cuando se sube su versión, entonces define CJ16 (sanciones y prescripción): cada sanción activa tiene `norma` y tarifa; una regla con `aplica_si: SISTEMA_DECLARACION` no puede estar activa si `sistema_determinacion` no es `DECLARACION` (CA‑5.08); `sancion_minima_uvt > 0` exige `uvt.valor`; `requiere_acto_en_firme` es `true`; `prescripcion.declaracion` es `SOLO_POR_ACTO_ADMINISTRATIVO` (PS‑01); `anios > 0`; `semaforo_dias.rojo < amarillo`; `antiguedad.rangos_dias` crece sin repetirse; y `prescripcion.competente` en `PENDIENTE` da una advertencia, no un error, y no cuenta para CJ02, porque solo hace falta para firmar el acto (5.4.4) (decisiones 7, 8 y 9). _Prueba:_ pendiente
- [ ] **005‑14** — Dado el Doc. 03, cuando se sube a la v0.5, entonces su §5.2 tiene las secciones `recaudo` (hoy falta) y `cartera`, su §7 registra los cambios que esta feature aplica a otros documentos, como lo hace hoy con los Docs. 01 y 02, y su §9.2 da el resultado del validador de Ipiales con CJ01 a CJ16: errores (ninguno, como pide CA‑5.13), advertencias (entre ellas `prescripcion.competente`) y reglas `POR_CONFIRMAR`. CJ02 dice que `prescripcion.competente` no cuenta (decisión 7). _Prueba:_ pendiente

**ERD 2.2**

- [ ] **005‑15** — Dado el ERD 2.1, cuando se sube a la 2.2, entonces tiene los ajustes del Doc. 05 §10 y los nueve del Doc. 04 §11 (ninguno está hoy en el ERD), con nombres en inglés, en el diagrama Mermaid de su esquema, en la lista de tablas y en las relaciones externas. El encabezado da el número de tablas nuevo y el motivo de la versión. Las tablas del Doc. 05 se resuelven reutilizando las del ERD, ampliadas (decisión 2). _Prueba:_ pendiente

**Esquema JSON 1.1**

- [ ] **CJ01** — Dados los dos YAML actualizados, con `esquema_version: "1.1"`, cuando se validan contra el esquema 1.1, entonces los dos son válidos. Lo prueban las pruebas del backend sobre sus copias, como en la feature 004. _Prueba:_ pendiente
- [ ] **005‑16** — Dado el esquema 1.0 publicado, cuando se agrega el 1.1, entonces el 1.1 es un archivo nuevo (`1.1.json`, con sus casos comunes en `1.1.casos.json`), el 1.0 y sus casos no cambian y su comprobación de inmutabilidad (004‑12) sigue en verde. _Prueba:_ pendiente
- [ ] **005‑17** — Dado el esquema 1.1, entonces cumple las reglas de forma del 1.0 en las secciones nuevas: objetos con `additionalProperties: false`, `estado` obligatorio donde lo traen los dos YAML, listas cerradas solo donde el YAML documenta todas las opciones, `PENDIENTE` solo en los campos que el equipo debe diligenciar (p. ej. `cuenta_recaudo`, `competente`, `estatuto_municipal`, `vigencias_desde`, `fecha_corte_migracion`) y ningún valor tributario. Las claves nuevas dentro de objetos que ya existen en el 1.0 son opcionales (005‑18); dentro de los objetos nuevos rige la regla general del 1.0. Ningún valor tributario quiere decir: ni la UVT, ni los puntos de la usura, ni el redondeo, ni los días del semáforo o de antigüedad (004‑05, 004‑08 y 004‑11). La meta‑prueba de los esquemas pasa. _Prueba:_ pendiente
- [ ] **005‑18** — Dado un YAML válido contra el 1.0, cuando solo cambia `esquema_version` a `"1.1"`, entonces es válido contra el 1.1: `recaudo.archivo_banco.formato` sigue aceptado, ahora opcional, y todo lo nuevo es opcional. El 1.1 es compatible con el 1.0, así que no es un cambio incompatible de §4.5 (decisión 10). _Prueba:_ pendiente
- [ ] **005‑19** — Dada la integración en `main` del PR del esquema, cuando corre `backend-main`, entonces se publica la *release* `jsonschema-config-jurisdiccion-v1.1` con el esquema, sus casos y sus SHA‑256 (ADR 0032). _Prueba:_ pendiente (se comprueba después de integrar)

**Reglas y mapa del repositorio**

- [ ] **005‑20** — Dado `AGENTS.md`, cuando se actualiza, entonces §1.1 y §3.1 incluyen el Doc. 05 en el orden de lectura (y ya no dicen que los intereses y las sanciones son «un futuro Módulo 5»), y §5.3 lista los prefijos de código de los Docs. 04 y 05 que hoy faltan (`OB`, `RP` e `IM` del Doc. 04; `CH`, `MO`, `SA` y `PS` del Doc. 05, decisión 1) y los ejemplos del Doc. 05 §4.3 entre los ejemplos numéricos. _Prueba:_ pendiente
- [ ] **005‑21** — Dadas las demás referencias a «documentos 01–04» (`README.md`, `spec/constitution/tech-stack.md`, el árbol de `docs/arquitectura/01` §14) y las que remiten al Módulo 5 (Doc. 02 §13, Doc. 04 §15, Doc. 01 §3), cuando se incorpora el Doc. 05, entonces remiten a él. _Prueba:_ pendiente
- [ ] **005‑22** — Dado el *roadmap*, cuando se cierra la feature, entonces la Fase 5 tiene sus features en el *backlog* a partir del menú del Doc. 05, y la Fase 6 (cobro y medidas cautelares) sigue «pendiente de su documento funcional». El glosario (`docs/arquitectura/01` §15) tiene los términos nuevos (cartera, causación, tramo, tasa de usura, interrupción y suspensión de la prescripción, corte de cartera, antigüedad, UVT, Asobancaria 2001, CSV canónico). Los escribe el orquestador (`AGENTS.md` §7). _Prueba:_ pendiente

## Fuera de alcance

- **La pasarela de pagos (Wompi)**, por decisión de la persona del 2026-11-08: el Doc. 04 v0.2 §12.3, WP‑01 a WP‑09, CA‑4.14 a CA‑4.17, `recaudo.pasarela` y el canal `PASARELA`. Llega después del piloto, con el portal del contribuyente (`spec/constitution/mission.md`; decisiones P8 y P9). El simulador de intereses del Doc. 05 §4.4 menciona su uso en el portal: se deja como uso futuro.
- **Código de negocio:** motor de intereses, causación, sanciones, prescripción, cortes, carga de cartera histórica, bancos, conciliación, tablas en la base y pantallas. Son las features de las Fases 4 y 5, que probarán los CA‑4.xx y CA‑5.xx.
- **Roles y permisos en el código** (*realm*, migraciones, catálogo del menú): no cambian (`AGENTS.md` §4.5). Lo decide la feature del Módulo 5 (decisión 3).
- **El validador CJ01–CJ16 y el de los anexos en la raíz:** se escriben en la feature de la opción 1.5.9. Aquí CJ15 y CJ16 solo se definen en el Doc. 03.
- **Las pruebas *golden*** (F0‑11): esta feature no las escribe, y F0‑11 no la espera. Sigue con los ejemplos de los Docs. 02, 03 y 04; los del Doc. 05 entran como *golden* con la feature del Módulo 5, cuando se resuelva el alcance del redondeo (decisión 14).
- **El Módulo 6** (cobro persuasivo y coactivo) y su documento funcional.
- **Los hallazgos 1, 2, 4 y 6 de la feature 004**, que esperan «la próxima versión del Doc. 03 y sus anexos»: no entran (H1). El hallazgo 3 (claves distintas entre plantilla e Ipiales) se respeta en las secciones nuevas (005‑11), sin tocar las que ya existen.

## Decisiones de la H1 (resueltas)

Decididas el 2026-11-08 por la persona que dirige el trabajo, que aceptó las 14 opciones recomendadas («Sí, todas (Recomendado)»). Se descartó la alternativa de cada una. Los criterios de arriba ya las aplican.

| # | Tema | Decisión |
|---|---|---|
| 1 | **Códigos del Doc. 05 que chocan** (005‑02). OB‑01 a OB‑05 ya son reglas de obligaciones (Doc. 04 §3.2) e IN‑01 a IN‑11 son reglas de independencia (`docs/arquitectura/01` §14.1). | Validaciones del lote OB‑01 a OB‑10 → **`CH‑01` a `CH‑10`** (cartera histórica); intereses IN‑01 a IN‑10 → **`MO‑01` a `MO‑10`** (mora); prescripción PR‑01 a PR‑04 → **`PS‑01` a `PS‑04`**, para no confundirlos con los *pull requests*; sanciones SA‑01 a SA‑05 sin cambio. |
| 2 | **Tablas nuevas del Doc. 05 §10 frente al ERD** (005‑07, 005‑15). El Doc. 05 crea `portfolio.collection_event`, `prescription_case` y `portfolio_snapshot`, y el ERD ya tiene `portfolio.prescription_event`, `prescription_evaluation` y `collection_portfolio_snapshot` con casi los mismos campos. Además, `collection_event` se confunde con el esquema `collection` del Módulo 6. | **Reutilizar las tablas del ERD** y agregarles los campos que faltan; el Doc. 05 cita los nombres del ERD. |
| 3 | **Roles del Doc. 05 §9** (005‑06): `ANALISTA_CARTERA`, `APROBADOR_CARTERA` y `ADMIN_PARAMETROS` no existen; el Doc. 01 §4.1 da el menú 5 a `RECAUDO`, `TESORERIA`, `APROBADOR_RECAUDO`, `JURIDICO`, `CONSULTA` y `AUDITOR` (provisional), y esos roles ya están en el código. | El Doc. 05 deja su matriz como **propuesta** y la marca como pendiente para la feature del Módulo 5; no cambia el Doc. 01 §4.1. |
| 4 | **Alcance del redondeo de la causación** (005‑04). Con causación mensual (IN‑08 b) y redondeo de cada causación a $1.000, el ejemplo 2 da $55.000 (siete causaciones de $8.000 y una de $7.000), no los $54.000 de CA‑5.03. | Registrar como **pendiente nuevo** de Hacienda: redondear cada causación o el acumulado de la obligación (causando la diferencia). CA‑5.03 dice que sus valores son para un cálculo único, sin cortes intermedios. |
| 5 | **Cómputo del término de prescripción** (005‑05). El ejemplo 1 del §6.3 cuenta desde la exigibilidad hasta el mismo día cinco años después (30‑abr‑2021 → 30‑abr‑2026). Los ejemplos 2 y 3 cuentan desde el día siguiente al evento hasta el día anterior (11‑mar‑2024 → 10‑mar‑2029). CA‑5.09 dice «día siguiente + 5 años». | Registrar como **pendiente de jurídica** y marcar los ejemplos como ilustrativos. |
| 6 | **Estado de lo que viene de prensa o espera validación** (005‑12). La UVT de $52.374 está como `VIGENTE` y la fórmula `SIMPLE_DIARIA` (tasa efectiva anual / 365) también, pero las dos esperan verificación (parte B, puntos 1 y 3). | **`POR_CONFIRMAR`** para `cartera.sanciones.uvt` y `cartera.intereses_mora`, hasta tener la fuente oficial y el visto bueno de jurídica. No bloquea la publicación: el aprobador marca la revisión (CJ02). |
| 7 | **`PENDIENTE` en una regla obligatoria** (CJ16). `cartera.prescripcion` tiene `obligatoria: true` y `competente: PENDIENTE`. Con CJ02, la configuración de Ipiales no se podría publicar, y el Doc. 03 §9.2 y CA‑5.13 dicen que pasa sin errores. | `competente` **no cuenta para CJ02**: CJ16 lo reporta como advertencia, porque solo hace falta para firmar el acto (5.4.4) y no cambia ningún cálculo. Los parámetros de cálculo (`anios`, `inicio_termino`, eventos) sí cuentan. |
| 8 | **Contenido de CJ15 y CJ16** (CJ15, CJ16). Ninguna fuente los define; el Doc. 05 y el YAML solo los nombran. | **CJ15:** si alguna modalidad activa liquida, existe `cartera.intereses_mora`; su `base` incluye `TAX` y no incluye `INTEREST` (sin anatocismo, MO‑01 y MO‑03); `bloquear_si_falta_tasa` es `true`; con redondeo `MULTIPLO_MAS_CERCANO`, el múltiplo es mayor que 0. **CJ16:** cada sanción activa tiene `norma` y tarifa; una regla con `aplica_si: SISTEMA_DECLARACION` no puede estar activa si la jurisdicción no usa declaración (decisión 9, CA‑5.08); `sancion_minima_uvt > 0` exige `uvt.valor`; `requiere_acto_en_firme` es `true`; `prescripcion.declaracion` es `SOLO_POR_ACTO_ADMINISTRATIVO` (PS‑01); `anios > 0`; `semaforo_dias.rojo < amarillo`; `antiguedad.rangos_dias` crece sin repetirse. |
| 9 | **Sistema de determinación** (CJ16, CA‑5.08). La configuración no dice si la jurisdicción liquida por liquidación‑factura o por declaración, y `aplica_si: SISTEMA_DECLARACION` lo necesita. | Agregar un campo cerrado, `sistema_determinacion: LIQUIDACION_FACTURA \| DECLARACION`, con `LIQUIDACION_FACTURA` en Ipiales (Doc. 05 §0) y `PENDIENTE` en la plantilla. Va en `cartera.sanciones` (plan, decisión P3). |
| 10 | **Compatibilidad del esquema 1.1** (005‑18). El YAML nuevo cambia `recaudo.archivo_banco.formato` por `formato_preferido`, `formato_alterno` y `formato_por_banco`. Quitar `formato` haría el cambio incompatible (`AGENTS.md` §4.5). | **1.1 compatible:** `formato` sigue aceptado como opcional y todo lo nuevo es opcional, así que un YAML válido en 1.0 lo es en 1.1 cambiando solo la versión. |
| 11 | **Canales sin pasarela** (005‑13). La v0.1 tenía `[BANCO_CONVENIO, PSE, VENTANILLA]`; en la v0.2, PSE llega por Wompi. | `canales: [BANCO_CONVENIO, VENTANILLA]`. Las menciones a PSE del Doc. 04 §4.1 y §6.2 llevan una nota: PSE llega con la pasarela, después del piloto. |
| 12 | **`Formatos_Intercambio_Recaudo.xlsx`** (005‑10). Tiene los *layouts* del CSV canónico v1 y del CSV de participación v1; descargarlo del proyecto de claude.ai necesita el permiso de la persona. | **Incorporarlo** a `docs/funcional/anexos/`, con su SHA‑256. La persona dio permiso y el orquestador lo descargó y lo verificó contra el original; se copia en la implementación. |
| 13 | **Número de CA‑4.18** (005‑09). | Mantener **CA‑4.18** y reservar CA‑4.14 a CA‑4.17 para la pasarela: conserva la trazabilidad con el proyecto de claude.ai. |
| 14 | **Orden frente a F0‑11** (pruebas *golden*). Los ejemplos del Doc. 05 §4.3 (CA‑5.03: $0, $54.000 y $181.000) serían casos *golden* nuevos. | **F0‑11 no espera a esta feature**: sigue con los ejemplos de los Docs. 02, 03 y 04, que cierran la Fase 0. Los del Doc. 05 entran como *golden* con la feature del Módulo 5, cuando se resuelva el redondeo (decisión 4). |

## Preguntas abiertas

La parte A (decisiones para la H1) pasó a «Decisiones de la H1 (resueltas)».

### B. Pendientes que se registran sin resolver

No cambian ningún criterio: se escriben en los documentos con su estado y su responsable (005‑03).

1. **UVT 2026 de $52.374** (Res. DIAN 000238 de 2025, según el Doc. 05 §0): verificarla contra la resolución oficial de la DIAN. El Doc. 05 la tomó de Gerencie.com y Siigo.
2. **Tasas de usura de enero a noviembre de 2026** (Doc. 05 §4.1): verificarlas contra las resoluciones de la Superfinanciera. Las de enero a septiembre vienen de prensa (El Espectador, La República, Valora Analitik, RCN Radio). Las de octubre y noviembre (Res. SFC 1707 de 2026) se consultaron, según el documento, «en la sesión anterior», sin PDF. Se cargan con su PDF en la 5.2.1, no van en la configuración.
3. **Fórmula de los intereses:** que jurídica valide la tasa efectiva anual dividida entre 365 (o 366) en interés simple. El art. 635 del ET habla de una «tasa de interés diario» equivalente a la usura menos dos puntos, y la fórmula del Doc. 05 §4.2 no usa la tasa diaria equivalente de la efectiva anual.
4. **Los 8 pendientes del Doc. 05 §11:**
   1. inicio del término de prescripción (ejecutoria de la factura o fecha límite de pago) — jurídica;
   2. redondeo de los intereses ($1.000 o al peso) — Hacienda;
   3. si las sanciones generan intereses (`sobre_sanciones`) — jurídica;
   4. sanciones del Estatuto de Rentas de Ipiales (Acuerdo 032 de 2018, mod. 028 de 2024) y si reduce la sanción mínima — jurídica;
   5. funcionario competente para decretar la prescripción y firmar sanciones — Hacienda;
   6. primera vigencia a migrar, fecha de corte de la migración y modo de intereses históricos — Hacienda y TI;
   7. tasas de usura de los años a migrar, si se usa `RECALCULAR` — TI;
   8. parametrización contable del corte (cuentas, deterioro) y cartera de difícil cobro (art. 820 ET) — contabilidad.
5. **Pendientes nuevos que esta feature agrega al Doc. 05 §11** (sin resolverlos):
   - alcance del redondeo de la causación (decisión 4) y cómputo del término de prescripción (decisión 5);
   - qué configuración rige la cartera de una vigencia anterior: la configuración es por jurisdicción y vigencia, y una obligación de 2021 causa intereses en 2026;
   - qué se ve del menú 5 según las modalidades activas: con solo `PARTICIPACION_20` no nacen obligaciones;
   - el Doc. 05 §4.3 cita `scripts/intereses_mora.py` como prueba de CA‑5.03, y ese *script* no está en el insumo ni en el repositorio: se quita la referencia y la prueba queda para la feature del Módulo 5.
6. **Pendientes del Doc. 04 v0.2 §14** que siguen abiertos: 1a (formato con cada banco), 1b (cuentas de recaudo), 2 (formato del reporte del 20 % con la Gobernación), 4, 5 y 6. El 1c (medios de Wompi y comisión) sale con la pasarela.
7. **Ejemplo 2 del Doc. 04 §5.2:** usa $15.000 de intereses al 15‑jul‑2026 como «valor ilustrativo». Con las reglas y las tasas del Doc. 05 darían unos $19.000. Se deja ilustrativo, con una nota, porque el ejemplo prueba la imputación y no el cálculo de intereses (es un caso *golden* de F0‑11).

### C. Para el plan (H2)

No cambian los criterios; los resuelve el `plan.md`.

- **Cómo se reparte en PR** (uno por proyecto, `AGENTS.md` §4.6): los documentos y los anexos en la raíz; el esquema 1.1 en el backend, con copias de los YAML nuevos en sus recursos de prueba. También en qué orden van: los anexos con `"1.1"` antes que el esquema publicado, o al revés, sabiendo que la validación de los anexos en la raíz pasó a la feature de la opción 1.5.9 (004‑13).
- **Detalles del esquema 1.1:**
  - `recaudo.archivo_banco.formato_por_banco: {}` es un mapa abierto, y la meta‑prueba exige `additionalProperties: false`;
  - `cartera.intereses_mora.tabla_tasas` nombra una tabla de la base dentro de la configuración;
  - `recaudo.estado_bancos` va suelto al lado de `bancos`;
  - `cartera_historica.vigencias_desde` es un año que hoy vale `PENDIENTE`, y el 1.0 no admite `PENDIENTE` en enteros;
  - qué listas se cierran (`inicio_termino`, `intereses_historicos`, `formula`…).
- **Fuente de las reglas de sanción:** el Doc. 01 §6.7 las registra en la opción 1.5.8 por formulario o archivo, y el Doc. 05 §5.1 las lee de la configuración. Es la misma tensión que hay hoy con las tarifas (1.5.5 frente a Doc. 03 §5.2). Se precisa el texto del Doc. 05, y la reconciliación completa queda para la feature de la opción 1.5.9.
- **ERD:**
  - si `portfolio.interest_rate` es nacional (sin `administration_id`) o por administración, y cómo encaja con el aislamiento por RLS (`AGENTS.md` §5.1);
  - dónde va el catálogo `ref.movement_type` del Doc. 04 §11, punto 8: el esquema `ref` no existe;
  - si el corte mensual necesita una cabecera para su estado (`ERROR`, publicado, reemplazo del Doc. 05 §7.1);
  - el número final de tablas.
- **Tamaño:** los documentos y los anexos superan las ~400 líneas; el plan decide cómo partirlos y lo justifica (Q12).
