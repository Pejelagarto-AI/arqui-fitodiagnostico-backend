package com.vivero.fitodiagnostico.dominio.excepcion;

import java.util.UUID;

/** RF-H4: no existe una solicitud de especie con ese id. La capa web la traduce a 404. */
public class SolicitudNoEncontradaException extends RuntimeException {

    private final UUID id;

    public SolicitudNoEncontradaException(UUID id) {
        super("no existe una solicitud de especie con id " + id);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
