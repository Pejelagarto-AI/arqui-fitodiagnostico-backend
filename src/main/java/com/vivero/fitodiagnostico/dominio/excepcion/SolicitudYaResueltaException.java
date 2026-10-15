package com.vivero.fitodiagnostico.dominio.excepcion;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoSolicitud;

/** RF-H4: la solicitud ya fue aprobada o rechazada; una resolución no se cambia. La capa web la traduce a 409. */
public class SolicitudYaResueltaException extends RuntimeException {

    private final EstadoSolicitud estado;

    public SolicitudYaResueltaException(EstadoSolicitud estado) {
        super("la solicitud ya fue resuelta (" + estado + ")");
        this.estado = estado;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }
}
