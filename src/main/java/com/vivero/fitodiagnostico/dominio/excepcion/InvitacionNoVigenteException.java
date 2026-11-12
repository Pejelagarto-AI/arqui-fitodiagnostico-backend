package com.vivero.fitodiagnostico.dominio.excepcion;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;

/**
 * La invitación existe pero ya no se puede usar (RN13): ya se aceptó, se
 * revocó o caducó. Lleva el estado para que el mensaje diga cuál de las tres
 * fue ("error claro"): el token es de 256 bits y no se puede adivinar, así
 * que decir qué le pasó no ayuda a nadie a enumerar nada. La capa web la
 * traduce a 410.
 */
public class InvitacionNoVigenteException extends RuntimeException {

    private final EstadoInvitacion estado;

    public InvitacionNoVigenteException(EstadoInvitacion estado) {
        super(mensajePara(estado));
        this.estado = estado;
    }

    public EstadoInvitacion getEstado() {
        return estado;
    }

    private static String mensajePara(EstadoInvitacion estado) {
        return switch (estado) {
            case ACEPTADA -> "la invitación ya fue aceptada: un enlace de invitación se usa una sola vez";
            case CADUCADA -> "la invitación caducó: pídele al dueño de la planta un enlace nuevo";
            case REVOCADA -> "la invitación fue revocada por el dueño de la planta";
            case PENDIENTE -> "la invitación está vigente";   // no debería lanzarse con este estado
        };
    }
}
