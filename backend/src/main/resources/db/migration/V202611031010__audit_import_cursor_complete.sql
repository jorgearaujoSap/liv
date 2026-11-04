-- Marca de agua del importador de eventos del proveedor (feature 001, T15).
--
-- complete dice si la última corrida leyó todo lo que el proveedor tenía. Si no (el proveedor tenía más eventos de
-- los que lee una corrida), la siguiente sigue justo desde la marca, sin volver a leer la superposición de 5
-- minutos: con una ráfaga densa, releer la superposición la llenaría siempre y la marca no avanzaría nunca.

ALTER TABLE audit.import_cursor ADD COLUMN complete BOOLEAN NOT NULL DEFAULT true;
