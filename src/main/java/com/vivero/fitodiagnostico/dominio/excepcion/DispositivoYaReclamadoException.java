package com.vivero.fitodiagnostico.dominio.excepcion;

/** El dispositivo ya pertenece a otra planta: antes hay que desvincularlo. La capa de entrada la traduce a 409. */
public class DispositivoYaReclamadoException extends RuntimeException {

    private final String serial;

    public DispositivoYaReclamadoException(String serial) {
        super("el dispositivo '" + serial + "' ya está reclamado por otra planta");
        this.serial = serial;
    }

    public String getSerial() {
        return serial;
    }
}
