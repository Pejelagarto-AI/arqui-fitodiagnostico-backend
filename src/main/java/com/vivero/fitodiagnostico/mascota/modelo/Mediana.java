package com.vivero.fitodiagnostico.mascota.modelo;

import java.util.Arrays;
import java.util.Collection;

/** La mediana y no el promedio: un pico aislado en la ventana no mueve la referencia (defensa 3 de la spec). */
final class Mediana {

    private Mediana() { }

    static double de(Collection<Double> valores) {
        if (valores.isEmpty()) {
            throw new IllegalArgumentException("la mediana de nada no existe");
        }
        double[] ordenados = valores.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(ordenados);
        int medio = ordenados.length / 2;
        return ordenados.length % 2 == 1 ? ordenados[medio] : (ordenados[medio - 1] + ordenados[medio]) / 2.0;
    }
}
