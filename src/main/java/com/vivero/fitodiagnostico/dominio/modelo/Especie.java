package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Umbrales de tolerancia de una especie. Es el agregado que la tabla de
 * referencia del Anexo B alimenta: un único nombre (no distingue nombre
 * científico de nombre común) y sus rangos óptimos por magnitud.
 */
public record Especie(
        String nombre,
        Map<Magnitud, Rango> rangos) {

    public Especie {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("la especie requiere nombre");
        }
        if (rangos == null || rangos.isEmpty()) {
            throw new IllegalArgumentException("la especie requiere al menos un rango de tolerancia");
        }
        rangos = Collections.unmodifiableMap(new EnumMap<>(rangos));
    }

    /** @throws IllegalArgumentException si la especie no tiene rango definido para esa magnitud. */
    public Rango rango(Magnitud magnitud) {
        Rango rango = rangos.get(magnitud);
        if (rango == null) {
            throw new IllegalArgumentException(
                "la especie " + nombre + " no tiene un rango definido para " + magnitud);
        }
        return rango;
    }

    public Set<Magnitud> magnitudes() {
        return rangos.keySet();
    }
}
