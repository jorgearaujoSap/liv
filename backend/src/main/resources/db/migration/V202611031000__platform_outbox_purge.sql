-- Purga del outbox (feature 001, T15; decisión del responsable del 2026-11-03).
--
-- Spring Modulith borra cada entrega al completarla (spring.modulith.events.completion-mode: delete): la tabla
-- solo guarda las entregas pendientes o fallidas, que se reentregan, y no crece con cada sesión. Para eso liv_app
-- necesita DELETE sobre platform.event_publication, la única tabla en la que lo tiene.
--
-- No se puede limitar el borrado a las entregas completadas con una política: en este modo Spring Modulith borra
-- la fila al completarla, sin marcar antes completion_date. Un borrado indebido de una entrega pendiente perdería
-- su auditoría; lo contienen la revisión del código (solo Spring Modulith borra) y la reentrega programada.

GRANT DELETE ON platform.event_publication TO liv_app;
