package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Puerto de entrada: el dueño comparte su planta e invita (RF-E1, RF-E3, RF-E4). */
public interface CompartirPlantaPort {

    /** La invitación guardada y el token en claro, que no se puede volver a obtener. */
    record InvitacionGenerada(Invitacion invitacion, String token) { }

    /** Un invitado de una planta, con desde cuándo tiene acceso (RF-E4). */
    record Invitado(Usuario usuario, Instant otorgadoEn) { }

    InvitacionGenerada generar(UUID solicitante, UUID plantaId, String correoOpcional);

    List<Invitacion> pendientes(UUID solicitante, UUID plantaId);

    void revocar(UUID solicitante, UUID plantaId, UUID invitacionId);

    List<Invitado> invitados(UUID solicitante, UUID plantaId);

    void revocarAcceso(UUID solicitante, UUID plantaId, UUID invitadoId);
}
