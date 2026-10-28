package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;

import java.time.Instant;
import java.util.UUID;

/**
 * Forma pública de una planta. {@code esDueno} le ahorra al front comparar ids
 * para decidir si muestra editar y eliminar (RN5); no es una medida de seguridad,
 * esa la hace el servidor en cada operación.
 */
public record RespuestaPlanta(UUID id, UUID duenoId, String apodo, String especie,
                              Instant registradaEn, boolean esDueno) {

    public static RespuestaPlanta desde(Planta planta, UUID solicitante) {
        return new RespuestaPlanta(planta.id(), planta.duenoId(), planta.apodo(),
                planta.especie().nombre(), planta.registradaEn(), planta.esDe(solicitante));
    }
}
