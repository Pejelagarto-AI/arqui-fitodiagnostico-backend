-- Fase 9: índices de las consultas de la mascota, en su propia migración para leerlos juntos.
--
-- La línea de tiempo (GET .../mascota/eventos y los cinco últimos de GET .../mascota) ordena por ocurrido_en
-- dentro de una planta, y desempata por registrado_en.
CREATE INDEX ix_evento_mascota_linea ON evento_mascota (planta_id, ocurrido_en DESC, registrado_en DESC);

-- "La acción PENDIENTE de esta planta": con cada lectura, con cada tic y en la consulta.
CREATE INDEX ix_accion_mascota_estado ON accion_mascota (planta_id, estado);

-- mascota no necesita índice extra: se busca siempre por planta_id, que es su clave primaria.
