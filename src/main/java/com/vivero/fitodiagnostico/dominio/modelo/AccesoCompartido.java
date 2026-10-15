package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/** Permiso de lectura de un invitado sobre una planta ajena (D2). Se crea, se revoca y se lista. */
public record AccesoCompartido(UUID plantaId, UUID invitadoId, Instant otorgadoEn) {

    public AccesoCompartido {
        if (plantaId == null || invitadoId == null || otorgadoEn == null) {
            throw new IllegalArgumentException("el acceso requiere planta, invitado y fecha");
        }
    }

    public static AccesoCompartido otorgadoAhora(UUID plantaId, UUID invitadoId) {
        return new AccesoCompartido(plantaId, invitadoId, Instant.now());
    }
}
