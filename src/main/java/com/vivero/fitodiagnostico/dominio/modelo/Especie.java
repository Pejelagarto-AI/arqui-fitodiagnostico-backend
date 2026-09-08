package com.vivero.fitodiagnostico.dominio.modelo;

/** Umbrales de tolerancia de una especie. Es el agregado que la BD alimenta. */
public record Especie(
        String nombreCientifico,
        String nombreComun,
        Rango temperatura,
        Rango humedad,
        Rango luz) {

    public Especie {
        if (nombreCientifico == null || nombreCientifico.isBlank()) {
            throw new IllegalArgumentException("la especie requiere nombre científico");
        }
    }
}
