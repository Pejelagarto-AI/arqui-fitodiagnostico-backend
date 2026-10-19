package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de vincular un dispositivo a una planta: el identificador que elige el dueño
 * (por ejemplo {@code esp32-01}). Valida solo la forma; el dominio vuelve a exigirla en
 * {@code TokenDeDispositivo}.
 */
public record SolicitudDispositivo(

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}",
                message = "debe tener de 1 a 64 letras, números, punto, guion o guion bajo")
        String dispositivoId) { }
