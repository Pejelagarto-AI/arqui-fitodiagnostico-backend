package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.List;

public record Diagnostico(
        Especie especie,
        Medicion medicion,
        String estado,
        String detalle,
        List<ResultadoParametro> parametros,
        Instant evaluadoEn) {

    public Diagnostico {
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
    }
}
