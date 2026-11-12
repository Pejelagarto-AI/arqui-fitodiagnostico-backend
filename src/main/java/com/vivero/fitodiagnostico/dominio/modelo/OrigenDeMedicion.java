package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * De dónde vino la medición que originó un diagnóstico (D17, RN20). Vive en
 * {@link DiagnosticoDePlanta} y no en {@link Medicion}: una medición suelta
 * (RF-F6) no tiene origen con sentido, y así {@code Medicion} queda intacta.
 */
public enum OrigenDeMedicion {
    /** Escrita a mano por el dueño. */
    MANUAL,
    /** Reportada por la maceta vinculada (módulo de macetas, bloque siguiente). */
    DISPOSITIVO
}
