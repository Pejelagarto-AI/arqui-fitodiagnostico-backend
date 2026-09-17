package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;

import java.util.ArrayList;
import java.util.List;

/**
 * Redacta una recomendación textual por cada parámetro fuera de rango (RF4).
 * Sin estado y sin nombrar ninguna {@link Magnitud} concreta: el texto de la
 * acción y la descripción de presentación vienen de la propia magnitud, así
 * que agregar una magnitud nueva no obliga a tocar esta clase (OCP), igual
 * que {@link ClasificadorDeParametros}.
 */
public final class RedactorDeRecomendaciones {

    /**
     * Una recomendación por cada parámetro fuera de rango, en el mismo orden
     * en que llegan los parámetros. Los parámetros OPTIMO no generan texto.
     */
    public List<String> redactar(List<ResultadoParametro> parametros) {
        List<String> recomendaciones = new ArrayList<>();
        for (ResultadoParametro parametro : parametros) {
            if (parametro.clasificacion() != Clasificacion.OPTIMO) {
                recomendaciones.add(textoPara(parametro));
            }
        }
        return List.copyOf(recomendaciones);
    }

    private String textoPara(ResultadoParametro parametro) {
        Magnitud magnitud = parametro.magnitud();
        Rango rangoOptimo = parametro.rangoOptimo();
        boolean bajo = parametro.clasificacion() == Clasificacion.BAJO;
        String comparacion = bajo ? "por debajo del" : "por encima del";
        String accion = bajo ? magnitud.accionSiBajo() : magnitud.accionSiAlto();

        return capitalizar(magnitud.descripcion()) + " " + numero(parametro.valor()) + " "
                + magnitud.unidad() + " " + comparacion + " rango óptimo "
                + numero(rangoOptimo.minimo()) + "–" + numero(rangoOptimo.maximo()) + " "
                + magnitud.unidad() + ": " + accion + ".";
    }

    private static String capitalizar(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Sin ".0" cuando el valor es un entero; con la parte decimal en cualquier otro caso. */
    private static String numero(double valor) {
        if (valor == Math.rint(valor) && !Double.isInfinite(valor)) {
            return String.valueOf((long) valor);
        }
        return String.valueOf(valor);
    }
}
