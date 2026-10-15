package com.vivero.fitodiagnostico.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /api/v1/auth/sesion} (RF-A2). Sin reglas de formato: una contraseña vieja que ya no las cumpla debe poder entrar. */
public record SolicitudInicioSesion(

        @NotBlank
        String correo,

        @NotBlank
        String contrasena) { }
