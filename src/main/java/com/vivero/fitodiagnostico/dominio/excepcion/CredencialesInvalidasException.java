package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * Correo o contraseña incorrectos al iniciar sesión (RF-A2). Un solo tipo y un
 * solo mensaje para "el correo no existe" y "la contraseña no coincide": decir
 * cuál de los dos falló le regala a un atacante la lista de correos registrados.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("correo o contraseña incorrectos");
    }
}
