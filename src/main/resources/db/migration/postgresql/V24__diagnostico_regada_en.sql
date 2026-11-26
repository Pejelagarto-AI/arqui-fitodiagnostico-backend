-- Fase 15 (planta recién regada, D203): el diagnóstico guarda cuándo se detectó el último riego de
-- las 24 h previas. Mientras está, la humedad ALTO no contó en contra (ReglaDeHumedadTrasRiego).
-- Se reconstruye desde su propia fila, como siempre (V7, V15).
--
-- Las filas anteriores quedan como lo que fueron: sin riego (NULL).
ALTER TABLE diagnostico_planta ADD COLUMN regada_en TIMESTAMP WITH TIME ZONE;
