-- Fuente de estos rangos: Anexo B del enunciado del proyecto. Reemplaza la
-- tabla especie original (nombre científico/común de plantas de ornato) por
-- la tabla de referencia formal del anexo: humedad del sustrato en %, luz en
-- lux, temperatura en °C, cinco especies mínimo.
DROP TABLE especie;

CREATE TABLE especie (
    id          BIGSERIAL      PRIMARY KEY,
    nombre      VARCHAR(120)   NOT NULL,
    humedad_min NUMERIC(6,1)   NOT NULL,
    humedad_max NUMERIC(6,1)   NOT NULL,
    luz_min     NUMERIC(6,1)   NOT NULL,
    luz_max     NUMERIC(6,1)   NOT NULL,
    temp_min    NUMERIC(6,1)   NOT NULL,
    temp_max    NUMERIC(6,1)   NOT NULL,
    CONSTRAINT uq_especie_nombre    UNIQUE (nombre),
    CONSTRAINT ck_especie_humedad   CHECK (humedad_min < humedad_max),
    CONSTRAINT ck_especie_luz       CHECK (luz_min     < luz_max),
    CONSTRAINT ck_especie_temp      CHECK (temp_min    < temp_max)
);

CREATE INDEX ix_especie_nombre_lower ON especie (LOWER(nombre));

INSERT INTO especie (nombre, humedad_min, humedad_max, luz_min, luz_max, temp_min, temp_max) VALUES
    ('sansevieria', 20.0, 45.0, 200.0,  1500.0, 15.0, 29.0),
    ('potos',       40.0, 70.0, 300.0,  1200.0, 18.0, 30.0),
    ('suculenta',   10.0, 30.0, 800.0,  2500.0, 15.0, 32.0),
    ('helecho',     60.0, 85.0, 150.0,  800.0,  16.0, 26.0),
    ('lavanda',     25.0, 50.0, 1000.0, 3000.0, 15.0, 30.0);
