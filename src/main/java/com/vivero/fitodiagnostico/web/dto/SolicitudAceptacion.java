package com.vivero.fitodiagnostico.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/invitaciones/aceptar} (RF-E2). El token va en el
 * cuerpo y no en la ruta (D50): una ruta queda en los registros de acceso del
 * servidor y de cualquier proxy.
 */
public record SolicitudAceptacion(

        @NotBlank @Size(max = 200)
        String token) { }
