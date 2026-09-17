package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Cada variable ambiental que el sistema sabe leer y evaluar. Agregar una
 * magnitud nueva (por ejemplo pH) es agregar una constante aquí con su unidad
 * y su rango físico de sensor: los datos, no el código, cargan la variación.
 */
public enum Magnitud {

    TEMPERATURA("temperatura", "°C", new Rango(-20.0, 60.0)),
    HUMEDAD("humedad relativa", "%", new Rango(0.0, 100.0)),
    LUZ("iluminancia", "lux", new Rango(0.0, 150_000.0));

    private final String descripcion;
    private final String unidad;
    private final Rango rangoFisico;

    Magnitud(String descripcion, String unidad, Rango rangoFisico) {
        this.descripcion = descripcion;
        this.unidad = unidad;
        this.rangoFisico = rangoFisico;
    }

    /** Nombre legible de la magnitud, usado al construir mensajes de error. */
    public String descripcion() {
        return descripcion;
    }

    public String unidad() {
        return unidad;
    }

    /** Rango físicamente posible para el sensor que mide esta magnitud. */
    public Rango rangoFisico() {
        return rangoFisico;
    }
}
