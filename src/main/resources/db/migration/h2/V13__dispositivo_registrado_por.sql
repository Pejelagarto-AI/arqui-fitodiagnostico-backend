-- Fase 3b: quien dio de alta el dispositivo. ADMIN = el endpoint de fabrica del administrador; FLOTA = el propio
-- chip, que se registra solo la primera vez que prende firmando con la clave de flota (D86). Las filas que ya
-- existian vienen todas de la fabrica manual, por eso el valor por defecto es ADMIN. Es solo para auditoria.
ALTER TABLE dispositivo ADD COLUMN registrado_por VARCHAR(16) DEFAULT 'ADMIN' NOT NULL;
