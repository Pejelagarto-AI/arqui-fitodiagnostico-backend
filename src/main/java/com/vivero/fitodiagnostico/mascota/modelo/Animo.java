package com.vivero.fitodiagnostico.mascota.modelo;

/**
 * Cómo se ve la mascota (D136). Es presentación derivada: sale del último diagnóstico y de la vitalidad, no se
 * guarda y el diagnóstico no lo conoce.
 */
public enum Animo {
    /** Todo en rango y con vitalidad. */
    FELIZ,
    /** Todo en rango, pero la vitalidad sigue baja: se está recuperando. */
    CANSADA,
    /** Humedad del sustrato BAJO. */
    SEDIENTA,
    /** Humedad del sustrato ALTO. */
    AHOGADA,
    /** Temperatura BAJO. */
    CON_FRIO,
    /** Temperatura ALTO. */
    CON_CALOR,
    /** Luz BAJO. Cuando exista {@code periodo} en el diagnóstico, de noche será DORMIDA (ver MapaDeAnimo). */
    A_OSCURAS,
    /** Luz ALTO. */
    DESLUMBRADA,
    /** Vitalidad en 0 y todavía no revive. Gana sobre todo lo demás. */
    DESMAYADA,
    /** La planta nunca se midió. */
    SIN_DATOS
}
