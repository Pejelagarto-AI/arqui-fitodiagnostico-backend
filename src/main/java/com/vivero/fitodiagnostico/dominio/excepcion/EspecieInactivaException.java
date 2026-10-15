package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * Se intentó registrar una planta, o cambiarle la especie, a una especie
 * descontinuada (RN11). No aplica a las plantas que ya la usan: esas siguen
 * funcionando con normalidad.
 */
public class EspecieInactivaException extends RuntimeException {

    private final String nombre;

    public EspecieInactivaException(String nombre) {
        super("la especie '" + nombre + "' está descontinuada y ya no se ofrece para plantas nuevas");
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
