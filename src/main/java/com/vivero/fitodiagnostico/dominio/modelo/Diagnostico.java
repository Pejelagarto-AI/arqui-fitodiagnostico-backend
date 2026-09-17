package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.List;

public record Diagnostico(
        Especie especie,
        Medicion medicion,
        EstadoGlobal estado,
        List<ResultadoParametro> parametros,
        List<String> recomendaciones,
        Instant evaluadoEn) {

    public Diagnostico {
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
        recomendaciones = recomendaciones == null ? List.of() : List.copyOf(recomendaciones);
    }
}
