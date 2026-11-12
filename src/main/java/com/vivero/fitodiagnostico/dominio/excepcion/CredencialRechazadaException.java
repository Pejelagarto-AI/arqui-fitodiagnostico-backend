package com.vivero.fitodiagnostico.dominio.excepcion;

/** El dispositivo no demostró conocer su secreto: no había reto vigente, expiró o la respuesta no coincide. Un mismo mensaje para los tres casos, para no ayudar a quien adivina. La capa de entrada la traduce a 401. */
public class CredencialRechazadaException extends RuntimeException {

    private final String serial;

    public CredencialRechazadaException(String serial) {
        super("credencial rechazada");
        this.serial = serial;
    }

    public String getSerial() {
        return serial;
    }
}
