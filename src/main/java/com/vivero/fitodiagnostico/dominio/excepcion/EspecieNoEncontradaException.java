package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * Se lanza cuando el nombre científico buscado no existe en la tabla de umbrales.
 * Es responsabilidad de la capa de aplicación decidir el código HTTP (404) a partir de esta excepción.
 */
public class EspecieNoEncontradaException extends RuntimeException {

    private final String nombreCientifico;

    public EspecieNoEncontradaException(String nombreCientifico) {
        super("no existe una especie registrada con nombre científico '" + nombreCientifico + "'");
        this.nombreCientifico = nombreCientifico;
    }

    public String getNombreCientifico() {
        return nombreCientifico;
    }
}
