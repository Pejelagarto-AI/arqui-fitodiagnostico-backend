package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Objects;

/** Resultado de comparar una lectura contra el rango óptimo de la especie para su magnitud. */
public record ResultadoParametro(
        Magnitud magnitud,
        double valor,
        Rango rangoOptimo,
        Clasificacion clasificacion) {

    public ResultadoParametro {
        Objects.requireNonNull(magnitud, "el resultado requiere una magnitud");
        Objects.requireNonNull(rangoOptimo, "el resultado requiere un rango óptimo");
        Objects.requireNonNull(clasificacion, "el resultado requiere una clasificación");
    }

    /** true si el valor cayó fuera del rango óptimo (BAJO o ALTO). */
    public boolean fueraDeRango() {
        return clasificacion != Clasificacion.OPTIMO;
    }
}
