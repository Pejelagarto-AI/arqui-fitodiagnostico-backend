-- Fase 13: el accesorio de la mascota (D182). Lo elige el dueño desde la app; la matera lo dibuja en la OLED.
-- Una columna en la fila que ya existe (V16): no es un evento de la línea de tiempo.
ALTER TABLE mascota ADD COLUMN accesorio VARCHAR(16) NOT NULL DEFAULT 'NINGUNO';
ALTER TABLE mascota ADD CONSTRAINT ck_mascota_accesorio CHECK (accesorio IN ('NINGUNO', 'SOMBRERO', 'MONO', 'GAFAS'));

-- "Los eventos que se anotaron después de tal instante" (respuesta de cada lectura de la matera, D180): por planta y
-- por hora de registro. La línea de tiempo de V19 ordena por ocurrido_en y no sirve para filtrar por registrado_en.
CREATE INDEX ix_evento_mascota_registro ON evento_mascota (planta_id, registrado_en DESC);
