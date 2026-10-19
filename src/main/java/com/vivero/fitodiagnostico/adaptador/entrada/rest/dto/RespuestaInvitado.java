package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.CompartirPlantaPort;

import java.time.Instant;
import java.util.UUID;

/** Un invitado de una planta, visible solo para su dueño (RF-E4). */
public record RespuestaInvitado(UUID id, String nombre, String correo, Instant otorgadoEn) {

    public static RespuestaInvitado desde(CompartirPlantaPort.Invitado invitado) {
        return new RespuestaInvitado(invitado.usuario().id(), invitado.usuario().nombre(),
                invitado.usuario().correo(), invitado.otorgadoEn());
    }
}
