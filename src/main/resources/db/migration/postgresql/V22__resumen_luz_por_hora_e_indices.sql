-- Fase 12 (D175 a D177): resumen de la luz por hora e índices.
--
-- (1) resumen_luz_hora: una fila por planta y hora CERRADA con lo que la regla de luz acumulada (fase 8) necesita de las
--     ~720 lecturas de esa hora: segundos observados, segundos con luz, lux por segundo de los tramos con luz los
--     segundos de oscuridad al final de la hora y el tramo con que empezó (para saber desde cuándo dura la oscuridad). Con ella, leer las últimas 24 h son 24 filas más las lecturas crudas
--     de dos horas parciales y no 17 000 lecturas. Se llena la primera vez que hace falta (la primera lectura después
--     de que la hora cierra) y se invalida si llega una lectura atrasada a esa hora. Una hora sin lecturas también
--     tiene fila (lecturas = 0): así no se recalcula. Se conserva para siempre; las lecturas crudas, no (D176).
CREATE TABLE resumen_luz_hora (
    planta_id                 UUID                     NOT NULL,
    hora                      TIMESTAMP WITH TIME ZONE NOT NULL,
    lecturas                  INTEGER                  NOT NULL,
    segundos_observados       DOUBLE PRECISION         NOT NULL,
    segundos_con_luz          DOUBLE PRECISION         NOT NULL,
    lux_segundos              DOUBLE PRECISION         NOT NULL,
    segundos_oscuridad_final  DOUBLE PRECISION         NOT NULL,
    primer_tramo_oscuro       DOUBLE PRECISION         NOT NULL,
    ultima_lectura_en         TIMESTAMP WITH TIME ZONE,
    calculado_en              TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (planta_id, hora),
    CONSTRAINT fk_resumen_luz_planta FOREIGN KEY (planta_id) REFERENCES planta (id) ON DELETE CASCADE
);

-- (2) El historial paginado y "el último" ordenan por (registrado_en DESC, id DESC) dentro de una planta. El índice de V7
--     no tenía el id: el motor filtraba por el índice pero ordenaba el desempate aparte. Con el id, la página sale
--     directo del índice.
DROP INDEX ix_diagnostico_historial;
CREATE INDEX ix_diagnostico_historial ON diagnostico_planta (planta_id, registrado_en DESC, id DESC);

-- (3) "La última caricia de esta planta" (límite de frecuencia, D172) filtra por tipo: sin esto recorría los eventos de
--     la planta de a uno hasta dar con una ACARICIADA.
CREATE INDEX ix_evento_mascota_tipo ON evento_mascota (planta_id, tipo, ocurrido_en DESC, registrado_en DESC);
