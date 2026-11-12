package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;

import java.util.List;
import java.util.UUID;

/** Puerto de entrada: un usuario propone una especie nueva y sigue sus solicitudes (RF-H1, RF-H2). */
public interface SolicitarEspeciePort {

    SolicitudEspecie enviar(UUID solicitanteId, String nombrePropuesto, String justificacion);

    List<SolicitudEspecie> deSolicitante(UUID solicitanteId);
}
