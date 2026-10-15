-- Corte 2, bloque 4, módulo E (RF-E1..E3, RN13, D12, D49, D51): invitaciones a una planta.
--
-- Solo se guarda la HUELLA (SHA-256 en hexadecimal) del token, nunca el token: quien lea esta
-- tabla (una copia de seguridad, un volcado) no puede aceptar invitaciones. La unicidad de la
-- huella es la red de seguridad de que dos invitaciones no compartan token.
-- El estado CADUCADA es parte del CHECK por completitud del enum, pero la aplicación no lo
-- escribe: se deriva de expira_en (ver Invitacion.estadoEn).
-- Sin ON DELETE CASCADE hacia planta, igual que V6 (D37): la cascada es regla de negocio y vive en
-- CascadaDeEliminacion; si un camino la olvida, esta clave foránea hace fallar el borrado.
CREATE TABLE invitacion (
    id             UUID                     PRIMARY KEY,
    planta_id      UUID                     NOT NULL,
    huella_token   VARCHAR(64)              NOT NULL,
    estado         VARCHAR(12)              NOT NULL,
    creada_en      TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_en      TIMESTAMP WITH TIME ZONE NOT NULL,
    correo_destino VARCHAR(254),
    aceptada_en    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_invitacion_huella UNIQUE (huella_token),
    CONSTRAINT fk_invitacion_planta FOREIGN KEY (planta_id) REFERENCES planta (id),
    CONSTRAINT ck_invitacion_estado CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'CADUCADA', 'REVOCADA'))
);

CREATE INDEX ix_invitacion_planta ON invitacion (planta_id, creada_en);
