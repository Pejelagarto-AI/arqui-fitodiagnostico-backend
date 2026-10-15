package com.vivero.fitodiagnostico.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/auth/recuperacion} (RF-A7). */
public record SolicitudRecuperacion(

        @NotBlank @Email @Size(max = 254)
        String correo) { }
