-- Fase 8 (luz acumulada y noche, D128): el diagnóstico guarda si la planta estaba de día o de
-- noche, cuánta luz recibió en las 24 h previas y, por parámetro, el valor que se comparó con el
-- rango. Se reconstruye desde su propia fila, como siempre (V7): si la regla cambia mañana, el
-- diagnóstico de hoy sigue diciendo lo que dijo.
--
-- Las filas anteriores quedan como lo que fueron: de día (DEFAULT 'DIA') y con la luz evaluada al
-- instante (luz_* y valor_evaluado en NULL).
ALTER TABLE diagnostico_planta ADD COLUMN periodo VARCHAR(10) DEFAULT 'DIA' NOT NULL;
ALTER TABLE diagnostico_planta ADD CONSTRAINT ck_diagnostico_periodo CHECK (periodo IN ('DIA', 'NOCHE'));
ALTER TABLE diagnostico_planta ADD COLUMN luz_horas_observadas DOUBLE PRECISION;
ALTER TABLE diagnostico_planta ADD COLUMN luz_horas_de_luz DOUBLE PRECISION;
ALTER TABLE diagnostico_planta ADD COLUMN luz_lux_promedio_en_luz DOUBLE PRECISION;
ALTER TABLE diagnostico_planta ADD COLUMN luz_lux_efectivo DOUBLE PRECISION;
ALTER TABLE diagnostico_planta ADD COLUMN luz_horas_en_oscuridad DOUBLE PRECISION;

ALTER TABLE diagnostico_parametro ADD COLUMN valor_evaluado DOUBLE PRECISION;
ALTER TABLE diagnostico_parametro ADD COLUMN base_evaluacion VARCHAR(12);
ALTER TABLE diagnostico_parametro ADD CONSTRAINT ck_parametro_base
    CHECK (base_evaluacion IN ('INSTANTANEA', 'ACUMULADA'));

-- Sirve a la serie de lux de las últimas 24 h (RepositorioDiagnosticos.serieDe), que filtra por
-- planta y por cuándo se TOMÓ la lectura, no por cuándo se registró.
CREATE INDEX ix_diagnostico_serie ON diagnostico_planta (planta_id, tomada_en);
