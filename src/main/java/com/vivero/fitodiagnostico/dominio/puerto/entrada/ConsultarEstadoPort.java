package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;

import java.util.Optional;
import java.util.UUID;

/** Puerto de entrada: el estado vigente de una planta (RF-F4). Dueño o invitado; nunca escribe. */
public interface ConsultarEstadoPort {

    /** El último diagnóstico guardado, o vacío si la planta nunca se midió (RN9). */
    Optional<DiagnosticoDePlanta> vigente(UUID solicitante, UUID plantaId);
}
