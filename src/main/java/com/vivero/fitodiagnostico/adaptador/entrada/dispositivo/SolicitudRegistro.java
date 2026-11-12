package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del auto-registro: el serial que el chip derivó de su MAC, el secreto que generó y la firma de flota
 * {@code base64( HMAC-SHA256( clave de flota, serial + "\n" + secreto ) )}.
 */
public record SolicitudRegistro(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}",
                message = "debe tener de 1 a 64 letras, números, punto, guion o guion bajo")
        String serial,

        @NotBlank @Size(min = 32, max = 128, message = "debe tener entre 32 y 128 caracteres")
        String secreto,

        @NotBlank String firma) { }
