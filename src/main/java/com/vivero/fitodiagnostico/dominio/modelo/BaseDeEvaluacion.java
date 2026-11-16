package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Con qué se comparó un parámetro contra su rango (D122). {@link #INSTANTANEA}:
 * el valor de esta lectura, como desde el corte 1. {@link #ACUMULADA}: un valor
 * calculado sobre el historial reciente de la planta (hoy, solo la luz de las
 * últimas 24 h). Es genérico a propósito: no nombra ninguna magnitud.
 */
public enum BaseDeEvaluacion {
    INSTANTANEA,
    ACUMULADA
}
