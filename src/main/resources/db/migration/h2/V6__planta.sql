-- Corte 2, módulo D (RF-D1..D5, RN6, RN7): plantas y accesos compartidos.
--
-- Sin ON DELETE CASCADE hacia planta ni hacia usuario, A PROPÓSITO: la cascada
-- de la RN12 (eliminar una planta elimina su historial y sus accesos; eliminar
-- una cuenta elimina sus plantas) es una regla de negocio y vive en
-- CascadaDeEliminacion, donde se prueba con dobles y donde además tiene que
-- avisar a los invitados. Si algún camino futuro se olvida de ella, esta clave
-- foránea hace fallar el borrado en vez de dejar filas huérfanas en silencio.
CREATE TABLE planta (
    id            UUID                     PRIMARY KEY,
    dueno_id      UUID                     NOT NULL,
    apodo         VARCHAR(60)              NOT NULL,
    especie_id    BIGINT                   NOT NULL,
    registrada_en TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_planta_dueno   FOREIGN KEY (dueno_id)   REFERENCES usuario (id),
    -- RN11: la especie nunca se elimina (D10), solo se desactiva; por eso la clave foránea es segura.
    CONSTRAINT fk_planta_especie FOREIGN KEY (especie_id) REFERENCES especie (id)
);

CREATE INDEX ix_planta_dueno ON planta (dueno_id, registrada_en);

-- D19: el invitado de una planta (lectura). Una fila por par; compartir dos
-- veces con la misma persona no duplica.
CREATE TABLE acceso_compartido (
    planta_id   UUID                     NOT NULL,
    invitado_id UUID                     NOT NULL,
    otorgado_en TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (planta_id, invitado_id),
    CONSTRAINT fk_acceso_planta   FOREIGN KEY (planta_id)   REFERENCES planta (id),
    CONSTRAINT fk_acceso_invitado FOREIGN KEY (invitado_id) REFERENCES usuario (id)
);

CREATE INDEX ix_acceso_invitado ON acceso_compartido (invitado_id);
