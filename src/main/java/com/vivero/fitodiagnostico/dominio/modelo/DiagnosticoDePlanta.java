package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Un {@link Diagnostico} inscrito en la vida de una planta (D5). ENVUELVE al
 * diagnóstico en vez de agregarle un campo {@code planta}: {@code Diagnostico}
 * sigue sirviendo sin planta ni cuenta (RF-F6), y un campo opcional es una
 * invitación permanente a olvidar comprobarlo.
 *
 * <p>{@code registradoEn} es cuándo entró al historial; {@code diagnostico.evaluadoEn()}
 * es cuándo corrió la evaluación. {@link #deUnaEvaluacion} los hace coincidir,
 * que es lo normal; el constructor permite que difieran para reconstruir
 * registros desde la base sin inventar fechas.
 */
public record DiagnosticoDePlanta(
        UUID id,
        UUID plantaId,
        OrigenDeMedicion origen,
        Instant registradoEn,
        Diagnostico diagnostico) {

    public DiagnosticoDePlanta {
        if (id == null || plantaId == null) {
            throw new IllegalArgumentException("el diagnóstico de planta requiere id y planta");
        }
        if (origen == null) {
            throw new IllegalArgumentException("el diagnóstico de planta requiere origen");
        }
        if (registradoEn == null || diagnostico == null) {
            throw new IllegalArgumentException("el diagnóstico de planta requiere fecha y diagnóstico");
        }
    }

    public static DiagnosticoDePlanta deUnaEvaluacion(UUID plantaId, OrigenDeMedicion origen,
                                                      Diagnostico diagnostico) {
        return new DiagnosticoDePlanta(UUID.randomUUID(), plantaId, origen,
                diagnostico.evaluadoEn(), diagnostico);
    }

    public EstadoGlobal estado() {
        return diagnostico.estado();
    }
}
