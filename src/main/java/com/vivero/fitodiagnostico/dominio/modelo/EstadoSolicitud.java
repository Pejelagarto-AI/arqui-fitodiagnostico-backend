package com.vivero.fitodiagnostico.dominio.modelo;

/** Ciclo de vida de una {@link SolicitudEspecie}: nace PENDIENTE y se resuelve una sola vez. */
public enum EstadoSolicitud {
    PENDIENTE,
    APROBADA,
    RECHAZADA
}
