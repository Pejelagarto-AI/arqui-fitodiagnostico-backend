package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo (opcional) de {@code POST /api/v1/plantas/{id}/invitaciones} (RF-E1).
 * Con {@code correo}, además de devolver el enlace se lo mandamos a esa
 * dirección; sin él, el dueño reparte el enlace como prefiera.
 */
public record SolicitudInvitacion(

        @Email @Size(max = 254)
        String correo) { }
