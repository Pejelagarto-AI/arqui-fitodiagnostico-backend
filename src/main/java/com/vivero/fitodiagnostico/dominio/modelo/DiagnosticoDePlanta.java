package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Un {@link Diagnostico} inscrito en la vida de una planta (D5). ENVUELVE al
 * diagnóstico en vez de agregarle un campo {@code planta}: {@code Diagnostico}
 * sigue sirviendo sin planta ni cuenta (RF-F6), y un campo opcional es una
 * invitación permanente a olvidar comprobarlo.
 *
 * <p>Tres instantes distintos: {@code tomadaEn} es cuándo se midió (lo informa
 * quien mide: el dispositivo manda su marca de tiempo y la entrada manual usa el
 * reloj del servidor); {@code registradoEn} es cuándo entró al historial y por él
 * se ordena "el último"; {@code diagnostico.evaluadoEn()} es cuándo corrió la
 * evaluación. Separar {@code tomadaEn} de {@code registradoEn} permite que un
 * dispositivo con el reloj desfasado, o que reenvía lecturas acumuladas, no
 * altere cuál es el diagnóstico vigente.
 */
public record DiagnosticoDePlanta(
        UUID id,
        UUID plantaId,
        OrigenDeMedicion origen,
        Instant tomadaEn,
        Instant registradoEn,
        Diagnostico diagnostico) {

    public DiagnosticoDePlanta {
        if (id == null || plantaId == null) {
            throw new IllegalArgumentException("el diagnóstico de planta requiere id y planta");
        }
        if (origen == null) {
            throw new IllegalArgumentException("el diagnóstico de planta requiere origen");
        }
        if (tomadaEn == null || registradoEn == null || diagnostico == null) {
            throw new IllegalArgumentException("el diagnóstico de planta requiere fechas y diagnóstico");
        }
    }

    /** Una medición tomada "ahora": {@code tomadaEn} coincide con el momento de la evaluación. */
    public static DiagnosticoDePlanta deUnaEvaluacion(UUID plantaId, OrigenDeMedicion origen,
                                                      Diagnostico diagnostico) {
        return deUnaEvaluacion(plantaId, origen, diagnostico.evaluadoEn(), diagnostico);
    }

    /** Una medición tomada en {@code tomadaEn} (la informa quien mide) y registrada ahora. */
    public static DiagnosticoDePlanta deUnaEvaluacion(UUID plantaId, OrigenDeMedicion origen,
                                                      Instant tomadaEn, Diagnostico diagnostico) {
        return new DiagnosticoDePlanta(UUID.randomUUID(), plantaId, origen, tomadaEn,
                diagnostico.evaluadoEn(), diagnostico);
    }

    public EstadoGlobal estado() {
        return diagnostico.estado();
    }
}
