package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Cada variable ambiental que el sistema sabe leer y evaluar. Agregar una
 * magnitud nueva (por ejemplo pH) es agregar una constante aquí con su unidad,
 * su rango físico de sensor y sus dos acciones de recomendación: los datos,
 * no el código, cargan la variación.
 *
 * <p>Orden de las constantes: HUMEDAD, LUZ, TEMPERATURA (RF1). Como
 * {@link Medicion} guarda las lecturas en un {@code EnumMap}, este orden
 * declarativo es también el orden en que se clasifican, se agregan y se
 * presentan los parámetros de un diagnóstico.
 */
public enum Magnitud {

    HUMEDAD("humedad del sustrato", "%", new Rango(0.0, 100.0),
            "regar moderadamente",
            "suspender el riego y revisar el drenaje"),

    LUZ("luz", "lux", new Rango(0.0, 150_000.0),
            "acercarla a una fuente de luz indirecta",
            "protegerla de la luz directa"),

    TEMPERATURA("temperatura", "°C", new Rango(-20.0, 60.0),
            "trasladar la planta a un lugar más cálido",
            "llevarla a un lugar más fresco y ventilado");

    private final String descripcion;
    private final String unidad;
    private final Rango rangoFisico;
    private final String accionSiBajo;
    private final String accionSiAlto;

    Magnitud(String descripcion, String unidad, Rango rangoFisico,
             String accionSiBajo, String accionSiAlto) {
        this.descripcion = descripcion;
        this.unidad = unidad;
        this.rangoFisico = rangoFisico;
        this.accionSiBajo = accionSiBajo;
        this.accionSiAlto = accionSiAlto;
    }

    /** Nombre legible de la magnitud, usado al construir mensajes de error y recomendaciones. */
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

    /** Recomendación textual cuando esta magnitud queda BAJO su rango óptimo (RF4). */
    public String accionSiBajo() {
        return accionSiBajo;
    }

    /** Recomendación textual cuando esta magnitud queda ALTO su rango óptimo (RF4). */
    public String accionSiAlto() {
        return accionSiAlto;
    }
}
