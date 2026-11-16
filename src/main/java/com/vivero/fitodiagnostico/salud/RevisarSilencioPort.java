package com.vivero.fitodiagnostico.salud;

/** Puerto de entrada del reloj: busca las materas vinculadas que callaron más de lo esperado. */
public interface RevisarSilencioPort {

    /** @return cuántas pasaron a {@link EstadoDeSalud#SIN_NOTICIAS} en esta pasada. */
    int revisar();
}
