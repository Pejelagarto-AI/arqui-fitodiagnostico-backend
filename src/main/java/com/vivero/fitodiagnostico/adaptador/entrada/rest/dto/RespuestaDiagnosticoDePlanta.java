package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;

import java.time.Instant;
import java.util.UUID;

/** Un diagnóstico del historial: el del contrato del Anexo A más su identidad, origen, cuándo se tomó y cuándo se registró (D17). */
public record RespuestaDiagnosticoDePlanta(UUID id, UUID plantaId, String origen, Instant tomadaEn,
                                           Instant registradoEn, RespuestaDiagnostico diagnostico) {

    public static RespuestaDiagnosticoDePlanta desde(DiagnosticoDePlanta registro) {
        return new RespuestaDiagnosticoDePlanta(registro.id(), registro.plantaId(), registro.origen().name(),
                registro.tomadaEn(), registro.registradoEn(), RespuestaDiagnostico.desde(registro.diagnostico()));
    }
}
