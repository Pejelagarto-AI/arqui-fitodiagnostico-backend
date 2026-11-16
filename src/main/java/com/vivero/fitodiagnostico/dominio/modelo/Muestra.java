package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.Objects;

/**
 * Un valor de una magnitud en un instante: un punto de una {@link SerieTemporal}.
 * No es una {@link Lectura}: no se valida contra el rango físico del sensor porque
 * sale de diagnósticos que ya se validaron al registrarse.
 */
public record Muestra(Instant tomadaEn, double valor) {

    public Muestra {
        Objects.requireNonNull(tomadaEn, "la muestra requiere el instante en que se tomó");
        if (Double.isNaN(valor) || Double.isInfinite(valor)) {
            throw new IllegalArgumentException("la muestra requiere un valor finito: " + valor);
        }
    }
}
