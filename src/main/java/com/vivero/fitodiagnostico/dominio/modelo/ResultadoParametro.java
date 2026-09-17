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

    /**
     * Qué tan lejos está el valor del rango óptimo, relativo al ancho de ese
     * rango: 0 en OPTIMO; para BAJO, (mínimo − valor) / (máximo − mínimo);
     * para ALTO, (valor − máximo) / (máximo − mínimo). Siempre ≥ 0. Es un
     * cálculo del modelo —no de ninguna regla de agregación— porque describe
     * este resultado, no una política de cuándo preocuparse.
     */
    public double desviacionRelativa() {
        double ancho = rangoOptimo.maximo() - rangoOptimo.minimo();
        return switch (clasificacion) {
            case OPTIMO -> 0.0;
            case BAJO -> (rangoOptimo.minimo() - valor) / ancho;
            case ALTO -> (valor - rangoOptimo.maximo()) / ancho;
        };
    }
}
