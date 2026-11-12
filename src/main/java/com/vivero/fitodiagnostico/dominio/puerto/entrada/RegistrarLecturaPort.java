package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de entrada: registrar una medición ya autenticada de una planta. Es el
 * puerto que usan los adaptadores que no son una persona (el dispositivo hoy,
 * MQTT mañana): quien lo invoque responde de haber verificado el origen.
 *
 * <p>Recibe todo lo que un canal nuevo necesita aportar y nada más: la planta, la
 * medición ya convertida a objetos de dominio, de dónde viene y cuándo se tomó. Un
 * canal nuevo (MQTT, LoRa, otro dispositivo) es otro adaptador que llama a este
 * mismo método; el dominio no cambia.
 */
public interface RegistrarLecturaPort {

    /** @throws com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException si la planta no existe */
    DiagnosticoDePlanta registrar(UUID plantaId, Medicion medicion, OrigenDeMedicion origen, Instant tomadaEn);
}
