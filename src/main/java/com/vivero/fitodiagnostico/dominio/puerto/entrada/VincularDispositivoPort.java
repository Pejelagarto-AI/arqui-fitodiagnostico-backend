package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;

import java.util.UUID;

/** Puerto de entrada: el dueño de una planta autoriza un dispositivo a reportar sus lecturas. */
public interface VincularDispositivoPort {

    /**
     * Solo el dueño (RN5): un invitado recibe {@code OperacionNoPermitidaException} y un ajeno, la misma
     * {@code PlantaNoEncontradaException} que si la planta no existiera.
     */
    TokenDeDispositivo vincular(UUID solicitante, UUID plantaId, String dispositivoId);
}
