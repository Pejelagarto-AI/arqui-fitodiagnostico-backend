package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;

/** Las tres variables ambientales de una lectura. Inmutable. */
public record Ambiente(double temperaturaC, double humedadRelativa, int luzLux) {

    private static final Rango SENSOR_TEMPERATURA = new Rango(-20.0, 60.0);
    private static final Rango SENSOR_HUMEDAD     = new Rango(0.0, 100.0);
    private static final Rango SENSOR_LUZ         = new Rango(0.0, 150_000.0);

    public Ambiente {
        exigirEnRango(SENSOR_TEMPERATURA, temperaturaC, "temperatura", "°C");
        exigirEnRango(SENSOR_HUMEDAD, humedadRelativa, "humedad relativa", "%");
        exigirEnRango(SENSOR_LUZ, luzLux, "iluminancia", "lux");
    }

    private static void exigirEnRango(Rango rango, double valor, String magnitud, String unidad) {
        if (!rango.contiene(valor)) {
            throw new LecturaInvalidaException(
                magnitud + " " + valor + " " + unidad + " fuera del rango físico del sensor");
        }
    }
}
