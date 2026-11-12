package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * El token (o el id) no corresponde a ninguna invitación, o la invitación no es
 * de la planta indicada. Un solo error para los dos casos: a quien presenta un
 * token inventado no se le dice nada más que "no existe". La capa web la
 * traduce a 404.
 */
public class InvitacionNoEncontradaException extends RuntimeException {

    public InvitacionNoEncontradaException() {
        super("la invitación no existe");
    }
}
