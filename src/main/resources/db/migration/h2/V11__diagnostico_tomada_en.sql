-- Fase 2 (arquitectura hexagonal): cuándo se TOMO la medicion, distinto de cuándo se REGISTRO.
-- El dispositivo informa su propia marca de tiempo; la entrada manual usa el reloj del servidor.
-- Las filas existentes se tomaron cuando se registraron.
ALTER TABLE diagnostico_planta ADD COLUMN tomada_en TIMESTAMP WITH TIME ZONE;
UPDATE diagnostico_planta SET tomada_en = registrado_en;
ALTER TABLE diagnostico_planta ALTER COLUMN tomada_en SET NOT NULL;
