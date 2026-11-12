-- Fase 3: dispositivos de fabrica que el dueno reclama por serial (reto HMAC, sin credencial copiada a mano).
--
-- serial es la clave y lo que va impreso en el sticker. El secreto se guarda EN CLARO a proposito: el
-- HMAC exige la clave en ambos lados y la alternativa (guardar un hash y que el chip mande el secreto por
-- la red) es peor sin TLS. Una fuga de la base expone secretos que solo sirven para reportar lecturas de
-- una planta, y el dueno los revoca desvinculando (ver docs/fase3/IMPLEMENTACION.md, D75).
-- planta_id es NULL mientras nadie lo reclama. reto y reto_expira son el desafio pendiente (de un solo uso).
-- Sin ON DELETE CASCADE hacia planta, igual que V6 y V8 (D37): la cascada vive en CascadaDeEliminacion,
-- que desvincula los dispositivos de la planta antes de borrarla.
CREATE TABLE dispositivo (
    serial               VARCHAR(64)              PRIMARY KEY,
    secreto              VARCHAR(128)             NOT NULL,
    planta_id            UUID,
    reclamado_en         TIMESTAMP WITH TIME ZONE,
    ultima_credencial_en TIMESTAMP WITH TIME ZONE,
    reto                 VARCHAR(128),
    reto_expira          TIMESTAMP WITH TIME ZONE,
    creado_en            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_dispositivo_planta FOREIGN KEY (planta_id) REFERENCES planta (id)
);

CREATE INDEX ix_dispositivo_planta ON dispositivo (planta_id);
