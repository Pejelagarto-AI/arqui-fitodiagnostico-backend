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
 *
 * <p>Fase 15 (D200, D203): {@code regadaEn} es cuándo se detectó el último riego de las 24 h
 * previas ({@code ReglaDeHumedadTrasRiego}), o {@code null} si no hubo. Mientras está, la humedad
 * ALTO no cuenta en contra: la planta está recién regada, no ahogada.
 */
public record Diagnostico(
        Especie especie,
        Medicion medicion,
        EstadoGlobal estado,
        List<ResultadoParametro> parametros,
        List<String> recomendaciones,
        Instant evaluadoEn,
        Periodo periodo,
        ExposicionLuminica exposicion,
        Instant regadaEn) {

    public Diagnostico {
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
        recomendaciones = recomendaciones == null ? List.of() : List.copyOf(recomendaciones);
        Objects.requireNonNull(periodo, "el diagnóstico requiere un periodo");
        if (periodo == Periodo.NOCHE && exposicion == null) {
            throw new IllegalArgumentException("solo con historial de luz se puede saber que es de noche");
        }
    }

    /** Sin riego reciente: la forma que tuvo de la fase 8 a la 14. */
    public Diagnostico(Especie especie, Medicion medicion, EstadoGlobal estado,
                       List<ResultadoParametro> parametros, List<String> recomendaciones, Instant evaluadoEn,
                       Periodo periodo, ExposicionLuminica exposicion) {
        this(especie, medicion, estado, parametros, recomendaciones, evaluadoEn, periodo, exposicion, null);
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

    /** Si hubo un riego en las 24 h previas a esta lectura. */
    public boolean recienRegada() {
        return regadaEn != null;
    }
}
