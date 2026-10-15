package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;

import java.util.Optional;

/**
 * RF-F4. Una planta sin diagnóstico no es un error (RN9): responde 200 con
 * {@code hayDiagnostico=false}, {@code diagnostico=null} y un mensaje. El front
 * distingue los dos casos por un booleano y no por un código de error ni por
 * adivinar si el objeto vino vacío.
 */
public record RespuestaDiagnosticoVigente(boolean hayDiagnostico, String mensaje,
                                          RespuestaDiagnosticoDePlanta diagnostico) {

    public static RespuestaDiagnosticoVigente desde(Optional<DiagnosticoDePlanta> vigente) {
        return vigente
                .map(registro -> new RespuestaDiagnosticoVigente(true, null, RespuestaDiagnosticoDePlanta.desde(registro)))
                .orElseGet(() -> new RespuestaDiagnosticoVigente(false,
                        "la planta todavía no tiene diagnósticos: registra una medición", null));
    }
}
