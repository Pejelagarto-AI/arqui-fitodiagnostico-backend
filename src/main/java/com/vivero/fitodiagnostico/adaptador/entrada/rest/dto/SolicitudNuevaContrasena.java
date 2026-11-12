package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/auth/restablecimiento} (RF-A8): el token del enlace y la contraseña nueva (mismos límites que el registro). */
public record SolicitudNuevaContrasena(

        @NotBlank @Size(max = 200)
        String token,

        @NotBlank @Size(min = 8, max = 72)
        String contrasenaNueva) { }
