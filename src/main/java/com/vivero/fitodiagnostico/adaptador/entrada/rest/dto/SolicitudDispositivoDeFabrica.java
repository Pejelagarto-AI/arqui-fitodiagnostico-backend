package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.Pattern;

/** Cuerpo (opcional) de dar de alta un dispositivo: si no trae serial, el servidor genera uno. */
public record SolicitudDispositivoDeFabrica(

        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}",
                message = "debe tener de 1 a 64 letras, números, punto, guion o guion bajo")
        String serial) { }
