-- Corte 2, bloque 4, módulo H (RF-H1..H5, D55, D56): solicitudes de especie nueva.
--
-- Sin ON DELETE CASCADE hacia usuario (D37): CascadaDeEliminacion las borra al eliminar la cuenta del solicitante.
CREATE TABLE solicitud_especie (
    id               UUID                     PRIMARY KEY,
    solicitante_id   UUID                     NOT NULL,
    nombre_propuesto VARCHAR(120)             NOT NULL,
    justificacion    VARCHAR(1000),
    estado           VARCHAR(10)              NOT NULL,
    respuesta        VARCHAR(1000),
    creada_en        TIMESTAMP WITH TIME ZONE NOT NULL,
    resuelta_en      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_solicitud_solicitante FOREIGN KEY (solicitante_id) REFERENCES usuario (id),
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'))
);

CREATE INDEX ix_solicitud_solicitante ON solicitud_especie (solicitante_id, creada_en);
CREATE INDEX ix_solicitud_estado ON solicitud_especie (estado, creada_en);
