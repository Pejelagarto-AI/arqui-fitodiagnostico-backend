-- Corte 2, módulo A (D1): un único tipo de usuario con un conjunto de roles.
-- El correo se guarda ya normalizado (minúscula, sin espacios; ver
-- Usuario.normalizarCorreo) y su unicidad (RN1) la garantiza este constraint
-- además de la comprobación previa de ServicioCuentas: es la red de seguridad
-- contra dos registros simultáneos.
-- RN10: solo se guarda el hash de la contraseña, nunca la contraseña.
CREATE TABLE usuario (
    id              UUID          PRIMARY KEY,
    nombre          VARCHAR(120)  NOT NULL,
    correo          VARCHAR(254)  NOT NULL,
    hash_contrasena VARCHAR(100)  NOT NULL,
    CONSTRAINT uq_usuario_correo UNIQUE (correo)
);

-- Un rol por fila (RN2: CLIENTE siempre, ADMINISTRADOR además). Tabla aparte
-- y no una columna booleana "es_admin" para que un tercer rol sea una fila
-- nueva y no una migración.
CREATE TABLE usuario_rol (
    usuario_id UUID        NOT NULL,
    rol        VARCHAR(20) NOT NULL,
    PRIMARY KEY (usuario_id, rol),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('CLIENTE', 'ADMINISTRADOR'))
);
