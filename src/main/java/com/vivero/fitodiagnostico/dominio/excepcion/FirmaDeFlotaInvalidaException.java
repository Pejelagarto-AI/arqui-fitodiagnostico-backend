package com.vivero.fitodiagnostico.dominio.excepcion;

/** La firma de flota de un auto-registro no coincide: quien se registra no conoce la clave de la flota. La capa de entrada la traduce a 401. */
public class FirmaDeFlotaInvalidaException extends RuntimeException {

    public FirmaDeFlotaInvalidaException() {
        super("la firma de flota no es válida");
    }
}
