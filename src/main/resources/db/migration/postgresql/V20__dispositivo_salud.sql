-- Fase 10: salud de la matera. Una fila por dispositivo con lo que la matera dice de sus tres sensores (OK o FALLA),
-- el estado de conjunto (OK, FALLA o SIN_NOTICIAS, que lo decide el servidor) y cuando cambio por ultima vez.
-- No es parte del dispositivo (V12) a proposito: el dominio no se toca en esta fase y la salud nace y muere con sus
-- propios reportes (D151). Si el dispositivo se borra, su salud se va con él. Sin fila = la matera nunca dejo noticia.
CREATE TABLE dispositivo_salud (
    serial              VARCHAR(64)              PRIMARY KEY,
    estado              VARCHAR(16)              NOT NULL,
    sensor_humedad      VARCHAR(8)               NOT NULL,
    sensor_luz          VARCHAR(8)               NOT NULL,
    sensor_temperatura  VARCHAR(8)               NOT NULL,
    detalle             VARCHAR(200),
    reportado_en        TIMESTAMP WITH TIME ZONE,
    cambio_en           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_dispositivo_salud_dispositivo FOREIGN KEY (serial) REFERENCES dispositivo (serial) ON DELETE CASCADE
);
