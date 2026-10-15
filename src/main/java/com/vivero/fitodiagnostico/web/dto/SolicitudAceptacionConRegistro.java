package com.vivero.fitodiagnostico.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/invitaciones/aceptar-con-registro} (RF-E2, visitante sin cuenta): el token y los datos del registro. */
public record SolicitudAceptacionConRegistro(

        @NotBlank @Size(max = 200)
        String token,

        @NotBlank @Size(max = 120)
        String nombre,

        @NotBlank @Email @Size(max = 254)
        String correo,

        @NotBlank @Size(min = 8, max = 72)
        String contrasena) { }
