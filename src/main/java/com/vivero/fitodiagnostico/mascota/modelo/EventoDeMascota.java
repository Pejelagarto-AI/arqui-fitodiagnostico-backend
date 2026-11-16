package com.vivero.fitodiagnostico.mascota.modelo;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Un evento de la línea de tiempo de la mascota. {@code ocurridoEn} es cuándo pasó (para REGADA y SALIO_AL_SOL, la
 * hora de la lectura que mostró el salto); el detalle son pares texto-texto ordenados por clave, para máquinas y
 * para leerlos en la base sin decodificar nada raro.
 */
public record EventoDeMascota(UUID id, UUID plantaId, TipoDeEvento tipo, Instant ocurridoEn,
                              Map<String, String> detalle) {

    public EventoDeMascota {
        if (id == null || plantaId == null || tipo == null || ocurridoEn == null) {
            throw new IllegalArgumentException("el evento de mascota requiere id, planta, tipo y hora");
        }
        detalle = detalle == null ? Map.of() : java.util.Collections.unmodifiableMap(new TreeMap<>(detalle));
    }

    public static EventoDeMascota nuevo(UUID plantaId, TipoDeEvento tipo, Instant ocurridoEn,
                                        Map<String, String> detalle) {
        return new EventoDeMascota(UUID.randomUUID(), plantaId, tipo, ocurridoEn, detalle);
    }
}
