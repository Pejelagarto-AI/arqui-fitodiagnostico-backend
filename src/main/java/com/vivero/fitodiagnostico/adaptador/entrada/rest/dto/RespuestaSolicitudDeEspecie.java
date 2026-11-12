package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;

import java.time.Instant;
import java.util.UUID;

/** Forma pública de una solicitud de especie (RF-H2, RF-H3). */
public record RespuestaSolicitudDeEspecie(UUID id, UUID solicitanteId, String nombre, String justificacion,
                                          String estado, String respuesta, Instant creadaEn, Instant resueltaEn) {

    public static RespuestaSolicitudDeEspecie desde(SolicitudEspecie s) {
        return new RespuestaSolicitudDeEspecie(s.id(), s.solicitanteId(), s.nombrePropuesto(), s.justificacion(),
                s.estado().name(), s.respuesta(), s.creadaEn(), s.resueltaEn());
    }
}
