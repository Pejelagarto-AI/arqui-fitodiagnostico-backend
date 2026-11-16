package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Cuánta luz recibió la planta en las últimas 24 h (D120, D123). Objeto de valor:
 * lo calcula {@code ReglaDeLuzAcumulada} a partir de la serie de lux del historial
 * más la lectura actual, y viaja dentro del {@link Diagnostico} para que quien lo
 * presente (front, OLED) pueda explicar el veredicto.
 *
 * @param horasObservadas   horas de la ventana que cubren las lecturas (cada lectura describe
 *                          el tramo desde la anterior, con un tope por hueco)
 * @param horasDeLuz        de esas, las horas con al menos el umbral de "hay luz"
 * @param luxPromedioEnLuz  promedio de lux, ponderado por tiempo, en las horas con luz (0 si no hubo)
 * @param luxEfectivo       el valor que se compara con el rango de la especie: la luz de las horas
 *                          con luz repartida en al menos las horas mínimas exigidas
 * @param horasEnOscuridad  cuánto lleva la planta sin luz, contando hasta esta lectura (0 si ahora hay luz)
 */
public record ExposicionLuminica(
        double horasObservadas,
        double horasDeLuz,
        double luxPromedioEnLuz,
        double luxEfectivo,
        double horasEnOscuridad) {

    private static final double TOLERANCIA = 1e-9;

    public ExposicionLuminica {
        noNegativo(horasObservadas, "horasObservadas");
        noNegativo(horasDeLuz, "horasDeLuz");
        noNegativo(luxPromedioEnLuz, "luxPromedioEnLuz");
        noNegativo(luxEfectivo, "luxEfectivo");
        noNegativo(horasEnOscuridad, "horasEnOscuridad");
        if (horasDeLuz > horasObservadas + TOLERANCIA) {
            throw new IllegalArgumentException("no puede haber más horas de luz (" + horasDeLuz
                    + ") que horas observadas (" + horasObservadas + ")");
        }
    }

    private static void noNegativo(double valor, String nombre) {
        if (Double.isNaN(valor) || Double.isInfinite(valor) || valor < 0) {
            throw new IllegalArgumentException(nombre + " debe ser un número finito y no negativo: " + valor);
        }
    }
}
