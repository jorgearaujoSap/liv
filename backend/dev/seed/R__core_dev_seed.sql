-- Datos ficticios de desarrollo (tarea T09 de la feature 001).
-- Los aplica Flyway solo con el perfil dev, desde backend/dev/seed (tarea T10): no se empaquetan en la imagen.
-- Solo entidades territoriales y administraciones; nunca datos de contribuyentes (AGENTS.md §4.5).
-- Los UUID son fijos: los usuarios de prueba de Keycloak (tarea T07) usan estos mismos valores en administration_id.
-- entity_type: DEPARTAMENTO, DISTRITO_CAPITAL, DISTRITO o MUNICIPIO (restricción de core.territorial_entity, migración de T10).

INSERT INTO core.territorial_entity (id, dane_code, name, entity_type, parent_id, active) VALUES
  ('00000000-0000-0000-0000-000000000052', '52',    'Nariño',                'DEPARTAMENTO', NULL,                                   true),
  ('00000000-0000-0000-0000-000000052356', '52356', 'Ipiales',               'MUNICIPIO',    '00000000-0000-0000-0000-000000000052', true),
  ('00000000-0000-0000-0000-0000000b0001', 'PRUEBA-B', 'Municipio de prueba B', 'MUNICIPIO',    NULL,                                   true)
ON CONFLICT (id) DO UPDATE SET dane_code = EXCLUDED.dane_code, name = EXCLUDED.name, entity_type = EXCLUDED.entity_type,
  parent_id = EXCLUDED.parent_id, active = EXCLUDED.active;

INSERT INTO core.tax_administration (id, territorial_entity_id, name, nit, address, active) VALUES
  ('10000000-0000-0000-0000-000000052356', '00000000-0000-0000-0000-000000052356', 'Municipio de Ipiales',       NULL, NULL, true),
  ('10000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-0000000b0001', 'Administración de prueba B', NULL, NULL, true)
ON CONFLICT (id) DO UPDATE SET territorial_entity_id = EXCLUDED.territorial_entity_id, name = EXCLUDED.name,
  nit = EXCLUDED.nit, address = EXCLUDED.address, active = EXCLUDED.active;
