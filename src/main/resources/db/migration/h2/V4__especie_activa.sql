-- D10: las especies se desactivan, nunca se eliminan. TRUE por defecto: todas
-- las especies existentes (y las que el CSV semilla cargue) nacen activas.
ALTER TABLE especie ADD COLUMN activa BOOLEAN DEFAULT TRUE NOT NULL;

-- D14: el CSV es la semilla y la base la única fuente en ejecución. V2 y V3
-- ya habían sembrado las cinco especies del Anexo B desde SQL; si quedaran,
-- la tabla nunca estaría vacía y el sembrador de CSV sería código muerto.
-- Se vacía aquí (los mismos cinco datos vuelven del CSV en el primer arranque)
-- y es seguro porque ninguna tabla referencia todavía a especie. Cuando exista
-- planta (con clave foránea a especie) esta migración ya habrá corrido.
DELETE FROM especie;
