CREATE TABLE especie (
    id                  BIGSERIAL PRIMARY KEY,
    nombre_cientifico   VARCHAR(120)  NOT NULL,
    nombre_comun        VARCHAR(120)  NOT NULL,
    temp_min            NUMERIC(4,1)  NOT NULL,
    temp_max            NUMERIC(4,1)  NOT NULL,
    hum_min             NUMERIC(4,1)  NOT NULL,
    hum_max             NUMERIC(4,1)  NOT NULL,
    luz_min             INTEGER       NOT NULL,
    luz_max             INTEGER       NOT NULL,
    CONSTRAINT uq_especie_nombre  UNIQUE (nombre_cientifico),
    CONSTRAINT ck_especie_temp    CHECK (temp_min < temp_max),
    CONSTRAINT ck_especie_hum     CHECK (hum_min  < hum_max AND hum_min >= 0 AND hum_max <= 100),
    CONSTRAINT ck_especie_luz     CHECK (luz_min  < luz_max AND luz_min >= 0)
);

CREATE INDEX ix_especie_nombre_lower ON especie (LOWER(nombre_cientifico));
