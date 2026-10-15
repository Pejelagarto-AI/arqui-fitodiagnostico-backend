package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Una invitación pendiente tal como la ve el dueño (RF-E3). NO trae el token:
 * solo se guarda su huella (D51) y el valor en claro se entrega una vez, al crearla
 * ({@link RespuestaInvitacionGenerada}).
 */
public record RespuestaInvitacion(UUID id, String estado, Instant creadaEn, Instant expiraEn, String correoDestino) {

    public static RespuestaInvitacion desde(Invitacion invitacion, Instant ahora) {
        return new RespuestaInvitacion(invitacion.id(), invitacion.estadoEn(ahora).name(), invitacion.creadaEn(),
                invitacion.expiraEn(), invitacion.correoDestino());
    }
}
