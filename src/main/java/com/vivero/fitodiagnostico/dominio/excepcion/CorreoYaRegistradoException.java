package com.vivero.fitodiagnostico.dominio.excepcion;

/** RN1: ya existe un usuario con ese correo. La capa web la traduce a 409. */
public class CorreoYaRegistradoException extends RuntimeException {

    private final String correo;

    public CorreoYaRegistradoException(String correo) {
        super("ya existe una cuenta con el correo '" + correo + "'");
        this.correo = correo;
    }

    public String getCorreo() {
        return correo;
    }
}
