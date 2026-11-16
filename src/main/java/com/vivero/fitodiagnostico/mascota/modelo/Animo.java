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
    /** Luz BAJO de día. De noche la misma luz es {@link #DORMIDA} (fase 12, D170). */
    A_OSCURAS,
    /** Luz ALTO. */
    DESLUMBRADA,
    /**
     * De noche ({@code Diagnostico.periodo() == NOCHE}) y lo único que falla es la luz: la planta duerme y la cara no
     * pide ayuda (fase 12, D170). Si falla otra cosa de noche, el ánimo es el de esa cosa.
     */
    DORMIDA,
    /** Vitalidad en 0 y todavía no revive. Gana sobre todo lo demás. */
    DESMAYADA,
    /** La planta nunca se midió. */
    SIN_DATOS
}
