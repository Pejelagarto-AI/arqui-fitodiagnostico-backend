-- Corte 2, bloque 4, módulo A (RF-A7, RF-A8, RN14, D13, D52): solicitudes de restablecimiento de contraseña.
--
-- huella_token: SHA-256 del token enviado por correo (el token no se guarda).
-- huella_credencial: SHA-256 del hash de la contraseña vigente cuando se pidió el enlace; si al usarlo la
-- credencial ya es otra, la contraseña cambió por otra vía y el enlace se invalida (RN14).
-- Sin ON DELETE CASCADE hacia usuario (D37): CascadaDeEliminacion borra estas filas al eliminar la cuenta.
CREATE TABLE restablecimiento (
    id                 UUID                     PRIMARY KEY,
    usuario_id         UUID                     NOT NULL,
    huella_token       VARCHAR(64)              NOT NULL,
    huella_credencial  VARCHAR(64)              NOT NULL,
    creada_en          TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_en          TIMESTAMP WITH TIME ZONE NOT NULL,
    usado              BOOLEAN                  NOT NULL,
    CONSTRAINT uq_restablecimiento_huella UNIQUE (huella_token),
    CONSTRAINT fk_restablecimiento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE INDEX ix_restablecimiento_usuario ON restablecimiento (usuario_id);
