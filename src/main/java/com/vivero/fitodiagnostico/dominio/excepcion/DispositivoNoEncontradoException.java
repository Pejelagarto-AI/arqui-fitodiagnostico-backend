package com.vivero.fitodiagnostico.dominio.excepcion;

/** No existe un dispositivo con ese serial (o no está en esa planta). La capa de entrada la traduce a 404. */
public class DispositivoNoEncontradoException extends RuntimeException {

    private final String serial;

    public DispositivoNoEncontradoException(String serial) {
        this("no existe un dispositivo con el serial '" + serial + "'", serial);
    }

    /** Con un mensaje propio, para decirle al usuario qué hacer (p. ej. cuando escanea antes de que la matera prenda). */
    public DispositivoNoEncontradoException(String mensaje, String serial) {
        super(mensaje);
        this.serial = serial;
    }

    public String getSerial() {
        return serial;
    }
}
