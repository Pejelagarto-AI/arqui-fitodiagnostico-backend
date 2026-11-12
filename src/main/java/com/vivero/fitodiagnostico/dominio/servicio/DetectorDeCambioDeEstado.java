package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;

/**
 * RN8: el estado global se compara siempre contra el diagnóstico
 * inmediatamente anterior de la MISMA planta (quien lo llama es quien trae el
 * anterior correcto). Solo mira {@code EstadoGlobal}: que un parámetro cambie
 * de valor sin cambiar el estado no es un cambio de estado.
 *
 * <p>El primer diagnóstico de una planta NO cuenta como cambio. No hay estado
 * anterior contra el cual cambiar, y avisar "tu planta pasó de nada a SALUDABLE"
 * entrena a ignorar los avisos. Que haya un diagnóstico nuevo se avisa por otro
 * camino (DIAGNOSTICO_NUEVO, RF-G2). Se descartó contarlo cuando nace en un
 * estado distinto de SALUDABLE: sería una segunda regla, con su umbral, que
 * nadie pidió.
 */
public final class DetectorDeCambioDeEstado {

    /**
     * @param anterior el diagnóstico previo de la planta, o {@code null} si es el primero
     * @param nuevo    el diagnóstico recién evaluado
     */
    public boolean huboCambio(Diagnostico anterior, Diagnostico nuevo) {
        if (nuevo == null) {
            throw new IllegalArgumentException("se requiere el diagnóstico nuevo");
        }
        return anterior != null && anterior.estado() != nuevo.estado();
    }
}
