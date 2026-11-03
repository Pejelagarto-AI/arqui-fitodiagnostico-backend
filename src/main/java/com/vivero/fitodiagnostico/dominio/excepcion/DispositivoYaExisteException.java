package com.vivero.fitodiagnostico.dominio.excepcion;

/** Ya existe un dispositivo de fábrica con ese serial. La capa de entrada la traduce a 409. */
public class DispositivoYaExisteException extends RuntimeException {

    private final String serial;

    public DispositivoYaExisteException(String serial) {
        super("ya existe un dispositivo con el serial '" + serial + "'");
        this.serial = serial;
    }

    public String getSerial() {
        return serial;
    }
}
