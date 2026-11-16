-- Fase 9: la línea de tiempo de la mascota. Solo se agrega; un evento no se edita.
-- ocurrido_en: cuándo pasó (para REGADA y SALIO_AL_SOL, la hora de la lectura del salto).
-- registrado_en: cuándo lo anotó el servidor; desempata la línea de tiempo.
-- detalle: JSON plano de texto a texto, para mostrar (D145). Mismo motivo de cascada que V16.
CREATE TABLE evento_mascota (
    id            UUID                     PRIMARY KEY,
    planta_id     UUID                     NOT NULL,
    tipo          VARCHAR(30)              NOT NULL,
    ocurrido_en   TIMESTAMP WITH TIME ZONE NOT NULL,
    registrado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    detalle       VARCHAR(1000)            NOT NULL,
    CONSTRAINT fk_evento_mascota_planta FOREIGN KEY (planta_id) REFERENCES planta (id) ON DELETE CASCADE,
    CONSTRAINT ck_evento_mascota_tipo CHECK (tipo IN ('REGADA', 'SALIO_AL_SOL', 'DESMAYO', 'REVIVIO', 'ACARICIADA',
        'RACHA', 'RIEGO_DECLARADO', 'RIEGO_CONFIRMADO', 'RIEGO_DESMENTIDO'))
);
