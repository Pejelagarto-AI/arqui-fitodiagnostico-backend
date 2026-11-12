package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * RN4: la operación dejaría al sistema sin administradores. La capa web la
 * traduce a 409: es un conflicto con el estado actual, no un dato mal formado.
 */
public class UltimoAdministradorException extends RuntimeException {

    public UltimoAdministradorException() {
        super("no se puede completar: es el último administrador del sistema");
    }
}
