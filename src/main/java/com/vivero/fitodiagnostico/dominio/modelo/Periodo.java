package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Si la planta está de día o de noche cuando se evaluó (D121). De noche la luz
 * no cuenta en contra del estado global: la planta duerme. Lo decide
 * {@code ReglaDeLuzAcumulada} con el historial; sin historial no hay con qué
 * saber que es de noche y el diagnóstico dice {@link #DIA}.
 */
public enum Periodo {
    DIA,
    NOCHE
}
