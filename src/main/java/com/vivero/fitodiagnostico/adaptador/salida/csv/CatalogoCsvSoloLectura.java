package com.vivero.fitodiagnostico.adaptador.salida.csv;

import com.vivero.fitodiagnostico.dominio.excepcion.CatalogoSoloLecturaException;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;

/**
 * El puerto de ESCRITURA del catálogo cuando la fuente es el CSV: cualquier
 * intento de modificar responde con {@link CatalogoSoloLecturaException} (409,
 * {@code CATALOGO_SOLO_LECTURA}). Se prefiere esto a un fallo silencioso o a
 * escribir en el CSV del classpath: el cambio se perdería al reiniciar y el
 * administrador creería que quedó guardado.
 */
public class CatalogoCsvSoloLectura implements CatalogoEditable {

    static final String MENSAJE = "el catálogo de especies se lee de un archivo CSV y no se puede modificar; "
            + "para administrarlo use fitodiagnostico.catalogo.fuente=bd";

    @Override
    public void guardar(Especie especie) {
        throw new CatalogoSoloLecturaException(MENSAJE);
    }

    @Override
    public void cambiarActivacion(String nombre, boolean activa) {
        throw new CatalogoSoloLecturaException(MENSAJE);
    }
}
