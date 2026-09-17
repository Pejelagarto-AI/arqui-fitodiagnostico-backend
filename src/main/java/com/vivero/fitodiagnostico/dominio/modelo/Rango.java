package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Un intervalo cerrado [minimo, maximo], inclusivo en ambos extremos (RF-08).
 * Inmutable; el invariante minimo &lt; maximo se valida en el constructor compacto (RA-11).
 */
public record Rango(double minimo, double maximo) {

    public Rango {
        if (minimo >= maximo) {
            throw new IllegalArgumentException("rango invertido: " + minimo + ".." + maximo);
        }
    }

    public boolean contiene(double valor)   { return valor >= minimo && valor <= maximo; }
    public boolean porDebajo(double valor)  { return valor < minimo; }
    public boolean porEncima(double valor)  { return valor > maximo; }

    /** Clasifica el valor frente a este rango; los extremos cuentan como OPTIMO. */
    public Clasificacion clasificar(double valor) {
        if (porDebajo(valor)) {
            return Clasificacion.BAJO;
        }
        if (porEncima(valor)) {
            return Clasificacion.ALTO;
        }
        return Clasificacion.OPTIMO;
    }
}
