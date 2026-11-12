package com.vivero.fitodiagnostico.dominio.excepcion;

/** RF-A5: la contraseña actual que dio el usuario no coincide con la guardada. */
public class ContrasenaActualIncorrectaException extends RuntimeException {

    public ContrasenaActualIncorrectaException() {
        super("la contraseña actual no es correcta");
    }
}
