package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;

import java.util.UUID;

/**
 * Puerto de entrada: registrar una medición ya autenticada de una planta. Es el
 * puerto que usan los adaptadores que no son una persona (el dispositivo hoy,
 * MQTT mañana): quien lo invoque responde de haber verificado el origen.
 */
public interface RegistrarLecturaPort {

    /** @throws com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException si la planta no existe */
    DiagnosticoDePlanta registrar(UUID plantaId, Medicion medicion, OrigenDeMedicion origen);
}
