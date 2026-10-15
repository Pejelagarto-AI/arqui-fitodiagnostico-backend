package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Umbrales de tolerancia de una especie. Es el agregado que la tabla de
 * referencia del Anexo B alimenta: un único nombre (no distingue nombre
 * científico de nombre común) y sus rangos óptimos por magnitud.
 *
 * <p>{@code activa} (D10, RN11): una especie descontinuada no se ofrece al
 * registrar plantas, pero sigue existiendo y sigue siendo diagnosticable.
 * Es el único cambio al modelo del núcleo en el corte 2: la decisión de
 * "¿se ofrece?" la toma la propia especie, no un método nuevo de los puertos.
 */
public record Especie(
        String nombre,
        Map<Magnitud, Rango> rangos,
        boolean activa) {

    public Especie {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("la especie requiere nombre");
        }
        if (rangos == null || rangos.isEmpty()) {
            throw new IllegalArgumentException("la especie requiere al menos un rango de tolerancia");
        }
        rangos = Collections.unmodifiableMap(new EnumMap<>(rangos));
    }

    /**
     * Una especie nueva nace activa. Conservar este constructor evita tocar
     * a todos los llamadores del corte 1, que nunca hablaron de activación.
     */
    public Especie(String nombre, Map<Magnitud, Rango> rangos) {
        this(nombre, rangos, true);
    }

    /** Copia de esta especie descontinuada; no se elimina nunca (D10). */
    public Especie desactivada() {
        return new Especie(nombre, rangos, false);
    }

    /** Copia de esta especie disponible de nuevo para ofrecerse (RF-C6). */
    public Especie reactivada() {
        return new Especie(nombre, rangos, true);
    }

    /** Copia con otros rangos óptimos y el mismo nombre y estado de activación (RF-C4). */
    public Especie conRangos(Map<Magnitud, Rango> nuevosRangos) {
        return new Especie(nombre, nuevosRangos, activa);
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
