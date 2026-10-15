-- Corte 2, módulo F (RF-F3..F5, D5, D17): historial de diagnósticos por planta.
--
-- Esquema elegido (D39): una fila por diagnóstico con el estado global, y tablas
-- hijas TIPADAS para los parámetros (uno por magnitud) y las recomendaciones.
-- Descartado: una columna JSON con todo el diagnóstico. Es más corto de
-- escribir, pero H2 y PostgreSQL lo tratan distinto (JSON/JSONB), y deja el
-- valor de cada magnitud ilegible para SQL: el plan de evolución pide mostrar
-- la serie temporal de cada magnitud, y con filas tipadas eso es un SELECT.
--
-- Los parámetros guardan el rango óptimo CON EL QUE se clasificó: si el
-- administrador edita la especie mañana, el diagnóstico de ayer sigue diciendo
-- lo que dijo.
CREATE TABLE diagnostico_planta (
    id             UUID                     PRIMARY KEY,
    planta_id      UUID                     NOT NULL,
    origen         VARCHAR(20)              NOT NULL,
    registrado_en  TIMESTAMP WITH TIME ZONE NOT NULL,
    evaluado_en    TIMESTAMP WITH TIME ZONE NOT NULL,
    -- El nombre de la especie al diagnosticar: la planta puede cambiar de especie después (RF-D4).
    especie_nombre VARCHAR(120)             NOT NULL,
    estado         VARCHAR(20)              NOT NULL,
    -- Sin ON DELETE CASCADE: ver V6. Se borra desde CascadaDeEliminacion.
    CONSTRAINT fk_diagnostico_planta FOREIGN KEY (planta_id) REFERENCES planta (id),
    CONSTRAINT ck_diagnostico_origen CHECK (origen IN ('MANUAL', 'DISPOSITIVO')),
    CONSTRAINT ck_diagnostico_estado CHECK (estado IN ('SALUDABLE', 'EN_RIESGO', 'CRITICO'))
);

-- Sirve a "el último" y a "el historial paginado": ambos ordenan por fecha dentro de una planta.
CREATE INDEX ix_diagnostico_historial ON diagnostico_planta (planta_id, registrado_en DESC);

-- Partes del diagnóstico, no agregados propios: ON DELETE CASCADE es correcto aquí
-- (mismo criterio que usuario_rol en V5) y permite borrar el historial de una
-- planta en una sola sentencia aunque tenga decenas de miles de filas.
CREATE TABLE diagnostico_parametro (
    diagnostico_id UUID             NOT NULL,
    magnitud       VARCHAR(20)      NOT NULL,
    valor          DOUBLE PRECISION NOT NULL,
    rango_minimo   DOUBLE PRECISION NOT NULL,
    rango_maximo   DOUBLE PRECISION NOT NULL,
    clasificacion  VARCHAR(10)      NOT NULL,
    PRIMARY KEY (diagnostico_id, magnitud),
    CONSTRAINT fk_parametro_diagnostico FOREIGN KEY (diagnostico_id)
        REFERENCES diagnostico_planta (id) ON DELETE CASCADE,
    CONSTRAINT ck_parametro_clasificacion CHECK (clasificacion IN ('BAJO', 'OPTIMO', 'ALTO'))
);

CREATE TABLE diagnostico_recomendacion (
    diagnostico_id UUID          NOT NULL,
    orden          INTEGER       NOT NULL,
    texto          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (diagnostico_id, orden),
    CONSTRAINT fk_recomendacion_diagnostico FOREIGN KEY (diagnostico_id)
        REFERENCES diagnostico_planta (id) ON DELETE CASCADE
);
