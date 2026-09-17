package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import java.util.List;

/**
 * Regla de agregación "por desviación" (RF3), la que el equipo decidió y
 * justifica así: mide qué tan lejos está cada parámetro de su rango óptimo,
 * no solo cuántos parámetros fallan, así que estar un grado fuera de rango
 * no pesa igual que estar muy fuera. SALUDABLE exige que todos estén OPTIMO;
 * CRITICO exige que al menos uno supere el umbral de desviación relativa
 * (exactamente el umbral cuenta como EN_RIESGO, no CRITICO); cualquier otro
 * caso con algo fuera de rango es EN_RIESGO. No nombra ninguna
 * {@code Magnitud} concreta, así que agregar una magnitud nueva nunca obliga
 * a abrir esta clase.
 */
public final class ReglaPorDesviacion implements ReglaDeAgregacion {

    public static final double UMBRAL_POR_DEFECTO = 0.25;

    private final double umbral;

    public ReglaPorDesviacion(double umbral) {
        if (umbral <= 0) {
            throw new IllegalArgumentException("el umbral debe ser mayor que 0: " + umbral);
        }
        this.umbral = umbral;
    }

    @Override
    public EstadoGlobal agregar(List<ResultadoParametro> parametros) {
        if (parametros == null || parametros.isEmpty()) {
            throw new IllegalArgumentException(
                "la regla de agregación requiere al menos un parámetro clasificado");
        }

        boolean algunoFueraDeRango = false;
        for (ResultadoParametro parametro : parametros) {
            if (parametro.fueraDeRango()) {
                algunoFueraDeRango = true;
                if (parametro.desviacionRelativa() > umbral) {
                    return EstadoGlobal.CRITICO;
                }
            }
        }
        return algunoFueraDeRango ? EstadoGlobal.EN_RIESGO : EstadoGlobal.SALUDABLE;
    }
}
