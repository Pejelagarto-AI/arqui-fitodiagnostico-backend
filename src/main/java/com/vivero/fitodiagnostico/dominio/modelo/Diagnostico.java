package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * El resultado de evaluar una medición contra una especie (RF2 a RF4).
 *
 * <p>Fase 8 (D121, D122): {@code periodo} dice si la planta estaba de día o de noche,
 * y {@code exposicion} cuánta luz recibió en las últimas 24 h. {@code exposicion} es
 * {@code null} cuando no había historial y la luz se evaluó con la lectura del
 * momento ({@link #baseDeLuz()} = INSTANTANEA): así queda el diagnóstico suelto de
 * RF-F6 y la primera medición de una planta.
 */
public record Diagnostico(
        Especie especie,
        Medicion medicion,
        EstadoGlobal estado,
        List<ResultadoParametro> parametros,
        List<String> recomendaciones,
        Instant evaluadoEn,
        Periodo periodo,
        ExposicionLuminica exposicion) {

    public Diagnostico {
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
        recomendaciones = recomendaciones == null ? List.of() : List.copyOf(recomendaciones);
        Objects.requireNonNull(periodo, "el diagnóstico requiere un periodo");
        if (periodo == Periodo.NOCHE && exposicion == null) {
            throw new IllegalArgumentException("solo con historial de luz se puede saber que es de noche");
        }
    }

    /** Diagnóstico instantáneo, de día y sin historial: la forma que tuvo hasta la fase 7. */
    public Diagnostico(Especie especie, Medicion medicion, EstadoGlobal estado,
                       List<ResultadoParametro> parametros, List<String> recomendaciones, Instant evaluadoEn) {
        this(especie, medicion, estado, parametros, recomendaciones, evaluadoEn, Periodo.DIA, null);
    }

    /** Si la luz se evaluó con la lectura del momento o con lo acumulado en el día. */
    public BaseDeEvaluacion baseDeLuz() {
        return exposicion == null ? BaseDeEvaluacion.INSTANTANEA : BaseDeEvaluacion.ACUMULADA;
    }
}
