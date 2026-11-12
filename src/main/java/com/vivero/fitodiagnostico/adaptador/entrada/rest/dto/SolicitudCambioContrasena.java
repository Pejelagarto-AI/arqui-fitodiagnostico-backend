package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PUT /api/v1/cuenta/contrasena} (RF-A5): exige la actual. */
public record SolicitudCambioContrasena(

        @NotBlank
        String contrasenaActual,

        @NotBlank @Size(min = 8, max = 72)
        String contrasenaNueva) { }
