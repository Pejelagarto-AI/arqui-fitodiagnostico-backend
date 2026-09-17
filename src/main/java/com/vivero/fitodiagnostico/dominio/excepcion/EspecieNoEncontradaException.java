package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * Se lanza cuando el nombre buscado no existe en la tabla de referencia.
 * Es responsabilidad de la capa de aplicación decidir el código HTTP (404) a partir de esta excepción.
 */
public class EspecieNoEncontradaException extends RuntimeException {

    private final String nombre;

    public EspecieNoEncontradaException(String nombre) {
        super("no existe una especie registrada con nombre '" + nombre + "'");
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
