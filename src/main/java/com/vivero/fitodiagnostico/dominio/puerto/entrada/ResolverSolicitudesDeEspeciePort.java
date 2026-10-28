package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;

import java.util.List;
import java.util.UUID;

/** Puerto de entrada del administrador: revisar y resolver solicitudes de especie (RF-H3 a RF-H5). */
public interface ResolverSolicitudesDeEspeciePort {

    List<SolicitudEspecie> pendientes();

    SolicitudEspecie aprobar(UUID solicitudId, String respuesta);

    SolicitudEspecie rechazar(UUID solicitudId, String respuesta);
}
