package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;

import java.util.UUID;

/**
 * Puerto de entrada: el dueño de una planta registra una medición escrita a
 * mano (RF-F1, origen MANUAL). Comprueba que el solicitante sea el dueño (RN5).
 */
public interface RegistrarMedicionManualPort {

    DiagnosticoDePlanta registrarManual(UUID solicitante, UUID plantaId, Medicion medicion);
}
