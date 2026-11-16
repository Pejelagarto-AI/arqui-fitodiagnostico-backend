-- Fase 10: cuando el servidor recibio la ultima lectura de la matera (hora del servidor, no la de la matera). Con
-- reportado_en (V20) es lo que mide el silencio. Nulo: nunca llego una lectura (D151).
ALTER TABLE dispositivo_salud ADD COLUMN ultima_lectura_en TIMESTAMP WITH TIME ZONE;
