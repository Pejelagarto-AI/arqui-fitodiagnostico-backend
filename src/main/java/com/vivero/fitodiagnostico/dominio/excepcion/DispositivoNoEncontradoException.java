package com.vivero.fitodiagnostico.dominio.excepcion;

/** No existe un dispositivo con ese serial (o no está en esa planta). La capa de entrada la traduce a 404. */
public class DispositivoNoEncontradoException extends RuntimeException {

    private final String serial;

    public DispositivoNoEncontradoException(String serial) {
        super("no existe un dispositivo con el serial '" + serial + "'");
        this.serial = serial;
    }

    public String getSerial() {
        return serial;
    }
}
