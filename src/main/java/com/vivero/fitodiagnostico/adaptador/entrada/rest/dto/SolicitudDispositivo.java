package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de reclamar un dispositivo: el serial impreso en el sticker (por ejemplo {@code fito-7K2M9Q}).
 * Valida solo la forma; el dominio vuelve a exigirla en {@code Dispositivo}.
 */
public record SolicitudDispositivo(

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}",
                message = "debe tener de 1 a 64 letras, números, punto, guion o guion bajo")
        String serial) { }
