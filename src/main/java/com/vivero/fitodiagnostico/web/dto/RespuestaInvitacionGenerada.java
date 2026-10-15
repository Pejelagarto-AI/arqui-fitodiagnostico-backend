package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que recibe el dueño al generar una invitación (RF-E1): el token y el
 * enlace al front que lo contiene. Es la ÚNICA vez que el token viaja en claro
 * desde el servidor; no se puede volver a consultar (D51).
 */
public record RespuestaInvitacionGenerada(UUID id, UUID plantaId, String token, String enlace, String estado,
                                          Instant expiraEn, String correoDestino) {

    public static RespuestaInvitacionGenerada desde(Invitacion invitacion, String token, String enlace) {
        return new RespuestaInvitacionGenerada(invitacion.id(), invitacion.plantaId(), token, enlace,
                invitacion.estado().name(), invitacion.expiraEn(), invitacion.correoDestino());
    }
}
