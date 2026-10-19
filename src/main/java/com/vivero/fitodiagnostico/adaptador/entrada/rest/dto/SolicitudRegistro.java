package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/auth/registro} (RF-A1). El borde valida la
 * forma; la unicidad del correo (RN1) la valida el servicio. El máximo de 72
 * en la contraseña no es capricho: BCrypt solo usa los primeros 72 bytes, y
 * aceptar más daría la falsa impresión de que todos cuentan.
 */
public record SolicitudRegistro(

        @NotBlank @Size(max = 120)
        String nombre,

        @NotBlank @Email @Size(max = 254)
        String correo,

        @NotBlank @Size(min = 8, max = 72)
        String contrasena) { }
