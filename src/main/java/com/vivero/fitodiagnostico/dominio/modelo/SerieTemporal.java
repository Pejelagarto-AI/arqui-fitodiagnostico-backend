package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Los valores de UNA magnitud de una planta a lo largo del tiempo, ordenados del
 * más antiguo al más reciente (D120). Es lo que la aplicación trae del historial
 * por el puerto {@code RepositorioDiagnosticos} y le entrega al dominio: datos en
 * bruto, sin interpretar. Qué significan (horas de luz, noche) lo decide
 * {@code ReglaDeLuzAcumulada}, no quien los trajo.
 */
public record SerieTemporal(List<Muestra> muestras) {

    private static final SerieTemporal VACIA = new SerieTemporal(List.of());

    public SerieTemporal {
        Objects.requireNonNull(muestras, "la serie requiere una lista de muestras (puede estar vacía)");
        List<Muestra> ordenadas = new ArrayList<>(muestras.size());
        for (Muestra muestra : muestras) {
            ordenadas.add(Objects.requireNonNull(muestra, "la serie no admite muestras nulas"));
        }
        ordenadas.sort(Comparator.comparing(Muestra::tomadaEn));
        muestras = List.copyOf(ordenadas);
    }

    /** Sin historial: la primera lectura de una planta, o un diagnóstico sin planta (RF-F6). */
    public static SerieTemporal vacia() {
        return VACIA;
    }

    public static SerieTemporal de(Collection<Muestra> muestras) {
        return new SerieTemporal(List.copyOf(muestras));
    }

    public boolean estaVacia() {
        return muestras.isEmpty();
    }

    /** Las muestras con {@code desde <= tomadaEn < hasta}, en orden. */
    public List<Muestra> entre(Instant desde, Instant hasta) {
        return muestras.stream()
                .filter(m -> !m.tomadaEn().isBefore(desde) && m.tomadaEn().isBefore(hasta))
                .toList();
    }
}
