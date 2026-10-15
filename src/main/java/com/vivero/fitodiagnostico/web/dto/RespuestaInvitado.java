package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.aplicacion.ServicioInvitaciones;

import java.time.Instant;
import java.util.UUID;

/** Un invitado de una planta, visible solo para su dueño (RF-E4). */
public record RespuestaInvitado(UUID id, String nombre, String correo, Instant otorgadoEn) {

    public static RespuestaInvitado desde(ServicioInvitaciones.Invitado invitado) {
        return new RespuestaInvitado(invitado.usuario().id(), invitado.usuario().nombre(),
                invitado.usuario().correo(), invitado.otorgadoEn());
    }
}
