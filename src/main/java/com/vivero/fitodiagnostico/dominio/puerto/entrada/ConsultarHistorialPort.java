package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;

import java.util.UUID;

/** Puerto de entrada: el historial paginado de diagnósticos de una planta (RF-F5). Dueño o invitado. */
public interface ConsultarHistorialPort {

    Pagina<DiagnosticoDePlanta> historial(UUID solicitante, UUID plantaId, Paginacion paginacion);
}
