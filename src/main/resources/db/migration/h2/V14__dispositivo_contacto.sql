-- Fase 5b: descubrimiento "a lo Chromecast". Cada vez que el dispositivo habla con el servidor (registro, reto,
-- credencial) se anota cuando y desde que direccion. Con eso el servidor sabe que materas sin dueño acaban de
-- aparecer en la misma red que el celular (D93 a D95). Nulos: las filas anteriores nunca dejaron constancia.
ALTER TABLE dispositivo ADD COLUMN ultimo_contacto_en TIMESTAMP WITH TIME ZONE;
ALTER TABLE dispositivo ADD COLUMN ultima_ip VARCHAR(64);
