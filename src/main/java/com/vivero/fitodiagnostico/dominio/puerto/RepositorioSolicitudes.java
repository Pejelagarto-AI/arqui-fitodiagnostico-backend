package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de las solicitudes de especie (RF-H1..H4). */
public interface RepositorioSolicitudes {

    /** Crea o reemplaza (por id). */
    void guardar(SolicitudEspecie solicitud);

    Optional<SolicitudEspecie> buscar(UUID id);

    /** Las pendientes, la más antigua primero: el administrador atiende en orden de llegada (RF-H3). */
    List<SolicitudEspecie> pendientes();

    /** Las del solicitante, la más reciente primero (RF-H2). */
    List<SolicitudEspecie> deSolicitante(UUID solicitanteId);

    /** Parte de eliminar la cuenta del solicitante (D37, D56). */
    void eliminarDe(UUID solicitanteId);
}
