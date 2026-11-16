-- Fase 9: la planta como mascota (D130 a D149, docs/fase9/IMPLEMENTACION.md).
-- V15 queda reservada a otro cambio en curso (el periodo día/noche del diagnóstico).
--
-- Una fila por planta con lo que la mascota recuerda: vitalidad, si está desmayada, el último estado que dijo el
-- sensor y desde cuándo, la racha y la hora de la última REGADA y del último SALIO_AL_SOL (enfriamiento). Nace con
-- la primera lectura de la planta.
--
-- ON DELETE CASCADE hacia planta, al revés que V6 a V12 (D37), a propósito (D144): la mascota es una proyección
-- del historial, no un agregado con reglas que avisen al borrarse. Con la cascada en la base, CascadaDeEliminacion
-- (aplicación del diagnóstico) no tiene que conocer a la mascota, que es lo que la fase 9 defiende.
CREATE TABLE mascota (
    planta_id         UUID                     PRIMARY KEY,
    vitalidad         DOUBLE PRECISION         NOT NULL,
    desmayada         BOOLEAN                  NOT NULL,
    ultimo_estado     VARCHAR(20),
    ultima_lectura_en TIMESTAMP WITH TIME ZONE,
    actualizada_en    TIMESTAMP WITH TIME ZONE NOT NULL,
    saludable_desde   TIMESTAMP WITH TIME ZONE,
    racha_dias        INTEGER                  NOT NULL,
    racha_desde       TIMESTAMP WITH TIME ZONE,
    ultima_regada_en  TIMESTAMP WITH TIME ZONE,
    ultimo_sol_en     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_mascota_planta FOREIGN KEY (planta_id) REFERENCES planta (id) ON DELETE CASCADE,
    CONSTRAINT ck_mascota_vitalidad CHECK (vitalidad >= 0 AND vitalidad <= 100),
    CONSTRAINT ck_mascota_racha CHECK (racha_dias >= 0),
    CONSTRAINT ck_mascota_estado CHECK (ultimo_estado IN ('SALUDABLE', 'EN_RIESGO', 'CRITICO'))
);
