package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * Se lanza cuando una lectura ambiental es sintácticamente válida pero físicamente
 * imposible para el rango del sensor (por ejemplo, una humedad relativa negativa).
 */
public class LecturaInvalidaException extends RuntimeException {

    public LecturaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
