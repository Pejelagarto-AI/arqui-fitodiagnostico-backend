package com.vivero.fitodiagnostico.dominio.excepcion;

/** El dispositivo pidió credencial pero nadie lo ha reclamado todavía: debe esperar y reintentar. La capa de entrada la traduce a 409. */
public class DispositivoSinReclamarException extends RuntimeException {

    private final String serial;

    public DispositivoSinReclamarException(String serial) {
        super("el dispositivo '" + serial + "' aún no ha sido reclamado por ninguna planta");
        this.serial = serial;
    }

    public String getSerial() {
        return serial;
    }
}
