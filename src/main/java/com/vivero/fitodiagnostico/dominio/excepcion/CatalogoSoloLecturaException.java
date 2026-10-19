package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * Se lanza cuando se intenta modificar un catálogo de especies que, por cómo
 * está configurada la fuente de la tabla de referencia, solo se puede leer
 * (por ejemplo, cuando sale de un archivo CSV). Es un conflicto con el estado
 * del sistema, no un dato mal formado: el borde lo responde con 409.
 */
public class CatalogoSoloLecturaException extends RuntimeException {

    public CatalogoSoloLecturaException(String mensaje) {
        super(mensaje);
    }
}
