package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Conjunto de lecturas tomadas en un mismo momento, una por magnitud como
 * máximo. Inmutable; se construye siempre a través de {@link #de}.
 */
public final class Medicion {

    private final EnumMap<Magnitud, Lectura> lecturas;

    private Medicion(EnumMap<Magnitud, Lectura> lecturas) {
        this.lecturas = lecturas;
    }

    public static Medicion de(Lectura... lecturas) {
        return de(Arrays.asList(lecturas));
    }

    public static Medicion de(Collection<Lectura> lecturas) {
        if (lecturas == null || lecturas.isEmpty()) {
            throw new IllegalArgumentException("una medición requiere al menos una lectura");
        }
        EnumMap<Magnitud, Lectura> mapa = new EnumMap<>(Magnitud.class);
        for (Lectura lectura : lecturas) {
            Objects.requireNonNull(lectura, "la medición no admite lecturas nulas");
            if (mapa.putIfAbsent(lectura.magnitud(), lectura) != null) {
                throw new IllegalArgumentException(
                    "lectura duplicada para la magnitud " + lectura.magnitud());
            }
        }
        return new Medicion(mapa);
    }

    /** @throws IllegalArgumentException si no hay lectura para esa magnitud. */
    public double valor(Magnitud magnitud) {
        Lectura lectura = lecturas.get(magnitud);
        if (lectura == null) {
            throw new IllegalArgumentException("la medición no tiene lectura para " + magnitud);
        }
        return lectura.valor();
    }

    public boolean contiene(Magnitud magnitud) {
        return lecturas.containsKey(magnitud);
    }

    /** En orden natural del enum {@link Magnitud}. */
    public Collection<Lectura> lecturas() {
        return Collections.unmodifiableCollection(lecturas.values());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Medicion otra)) return false;
        return lecturas.equals(otra.lecturas);
    }

    @Override
    public int hashCode() {
        return lecturas.hashCode();
    }

    @Override
    public String toString() {
        return "Medicion" + lecturas.values();
    }
}
