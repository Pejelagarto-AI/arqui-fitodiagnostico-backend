package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Objects;

/**
 * Resultado de comparar una magnitud contra el rango óptimo de la especie.
 *
 * <p>{@code valor} es lo que se leyó en esta medición. {@code valorEvaluado} es lo
 * que se comparó con el rango y de donde sale la clasificación (D122). Son el mismo
 * número cuando la {@code base} es {@link BaseDeEvaluacion#INSTANTANEA}, que es el
 * caso de todas las magnitudes salvo la luz con historial: ahí {@code valorEvaluado}
 * es el lux efectivo de las últimas 24 h ({@link ExposicionLuminica#luxEfectivo()}).
 */
public record ResultadoParametro(
        Magnitud magnitud,
        double valor,
        Rango rangoOptimo,
        Clasificacion clasificacion,
        double valorEvaluado,
        BaseDeEvaluacion base) {

    public ResultadoParametro {
        Objects.requireNonNull(magnitud, "el resultado requiere una magnitud");
        Objects.requireNonNull(rangoOptimo, "el resultado requiere un rango óptimo");
        Objects.requireNonNull(clasificacion, "el resultado requiere una clasificación");
        Objects.requireNonNull(base, "el resultado requiere la base con que se evaluó");
        if (base == BaseDeEvaluacion.INSTANTANEA && Double.compare(valor, valorEvaluado) != 0) {
            throw new IllegalArgumentException(
                "una evaluación instantánea compara el valor leído, no otro: " + valor + " vs " + valorEvaluado);
        }
    }

    /** Evaluación instantánea: el valor leído es el que se compara (corte 1). */
    public ResultadoParametro(Magnitud magnitud, double valor, Rango rangoOptimo, Clasificacion clasificacion) {
        this(magnitud, valor, rangoOptimo, clasificacion, valor, BaseDeEvaluacion.INSTANTANEA);
    }

    /** true si el valor evaluado cayó fuera del rango óptimo (BAJO o ALTO). */
    public boolean fueraDeRango() {
        return clasificacion != Clasificacion.OPTIMO;
    }

    /**
     * Qué tan lejos está el valor evaluado del rango óptimo, relativo al ancho de ese
     * rango: 0 en OPTIMO; para BAJO, (mínimo − valorEvaluado) / (máximo − mínimo);
     * para ALTO, (valorEvaluado − máximo) / (máximo − mínimo). Siempre ≥ 0. Es un
     * cálculo del modelo —no de ninguna regla de agregación— porque describe
     * este resultado, no una política de cuándo preocuparse.
     */
    public double desviacionRelativa() {
        double ancho = rangoOptimo.maximo() - rangoOptimo.minimo();
        return switch (clasificacion) {
            case OPTIMO -> 0.0;
            case BAJO -> (rangoOptimo.minimo() - valorEvaluado) / ancho;
            case ALTO -> (valorEvaluado - rangoOptimo.maximo()) / ancho;
        };
    }
}
