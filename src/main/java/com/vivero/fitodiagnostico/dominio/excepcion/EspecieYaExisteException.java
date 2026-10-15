package com.vivero.fitodiagnostico.dominio.excepcion;

/** RF-C3: ya hay una especie con ese nombre (activa o no). La capa web la traduce a 409. */
public class EspecieYaExisteException extends RuntimeException {

    private final String nombre;

    public EspecieYaExisteException(String nombre) {
        super("ya existe una especie registrada con nombre '" + nombre + "'");
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
