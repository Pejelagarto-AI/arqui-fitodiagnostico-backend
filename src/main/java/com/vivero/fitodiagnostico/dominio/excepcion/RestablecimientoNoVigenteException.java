package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * El enlace de restablecimiento no sirve (RN14): no existe, ya se usó, caducó o
 * la contraseña cambió por otra vía. Un solo mensaje para los cuatro casos:
 * este endpoint es público y no hay razón para contarle a un desconocido cuál
 * fue. La capa web la traduce a 410.
 */
public class RestablecimientoNoVigenteException extends RuntimeException {

    public RestablecimientoNoVigenteException() {
        super("el enlace de restablecimiento no es válido: ya se usó, caducó o la contraseña cambió; "
                + "solicita uno nuevo");
    }
}
