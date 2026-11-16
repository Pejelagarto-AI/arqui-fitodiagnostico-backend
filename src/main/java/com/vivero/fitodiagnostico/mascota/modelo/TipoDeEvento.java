package com.vivero.fitodiagnostico.mascota.modelo;

/**
 * Lo que le puede pasar a la mascota (D133). Cada evento se guarda con hora y detalle y se empuja al front y a la
 * matera. La frase es para personas (el campo {@code mensaje} del aviso); el front y la OLED animan por el tipo.
 */
public enum TipoDeEvento {

    /** La humedad del sustrato subió de golpe y la lectura siguiente lo confirmó (D134). */
    REGADA("«%s» recibió agua."),
    /** La luz subió de golpe y se sostuvo dos lecturas (D135). */
    SALIO_AL_SOL("«%s» salió al sol."),
    /** La vitalidad llegó a 0 (D131). */
    DESMAYO("«%s» se desmayó. Necesita ayuda."),
    /** Volvió a estar saludable un rato después de desmayarse (D131). */
    REVIVIO("«%s» revivió."),
    /** El dueño la acarició (D137). No cambia la vitalidad. */
    ACARICIADA("Acariciaron a «%s»."),
    /** Sumó un día completo sin pasar por CRITICO (D132). */
    RACHA("«%s» suma otro día sin estar crítica."),
    /** El dueño dijo "la regué" y el sistema espera al sensor (D138). */
    RIEGO_DECLARADO("Dijeron que regaron a «%s»; esperando al sensor."),
    /** El sensor confirmó el riego declarado. */
    RIEGO_CONFIRMADO("El sensor confirmó el riego de «%s»."),
    /** El plazo venció sin que el sensor viera el riego declarado. */
    RIEGO_DESMENTIDO("El sensor no vio el riego de «%s».");

    private final String plantilla;

    TipoDeEvento(String plantilla) {
        this.plantilla = plantilla;
    }

    public String frase(String apodo) {
        return String.format(plantilla, apodo);
    }
}
