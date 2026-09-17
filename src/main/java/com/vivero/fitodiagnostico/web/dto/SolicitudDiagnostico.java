package com.vivero.fitodiagnostico.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del POST del contrato del Anexo A (sección 2). Bean Validation cubre
 * solo ausencia/blanco; lo físicamente imposible lo valida el dominio al
 * construir {@code Lectura} (RA6: el borde valida forma, el dominio valida
 * negocio).
 */
public record SolicitudDiagnostico(

        @NotBlank
        String especie,

        @NotNull
        Double humedad,

        @NotNull
        Double luz,

        @NotNull
        Double temperatura) { }
