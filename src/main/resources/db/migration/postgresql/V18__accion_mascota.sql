-- Fase 9: los "la regué" del dueño, que el sensor confirma o desmiente (D138).
-- humedad_referencia: mediana de la humedad de la última hora al declarar; nula si la planta no tenía lecturas.
-- Acariciar no se guarda aquí: es solo un evento (V17). Mismo motivo de cascada que V16.
CREATE TABLE accion_mascota (
    id                 UUID                     PRIMARY KEY,
    planta_id          UUID                     NOT NULL,
    tipo               VARCHAR(20)              NOT NULL,
    estado             VARCHAR(20)              NOT NULL,
    creada_en          TIMESTAMP WITH TIME ZONE NOT NULL,
    vence_en           TIMESTAMP WITH TIME ZONE,
    resuelta_en        TIMESTAMP WITH TIME ZONE,
    humedad_referencia DOUBLE PRECISION,
    CONSTRAINT fk_accion_mascota_planta FOREIGN KEY (planta_id) REFERENCES planta (id) ON DELETE CASCADE,
    CONSTRAINT ck_accion_mascota_tipo CHECK (tipo IN ('ACARICIAR', 'LA_REGUE')),
    CONSTRAINT ck_accion_mascota_estado CHECK (estado IN ('HECHA', 'PENDIENTE', 'CONFIRMADA', 'DESMENTIDA'))
);
