package com.vivero.fitodiagnostico.salud;

import java.util.Arrays;
import java.util.Optional;

/** Los tres sensores de la matera, con el nombre que usa el contrato ({@code humedad}, {@code luz}, {@code temperatura}). */
public enum SensorDeMatera {
    HUMEDAD("humedad", "la humedad"),
    LUZ("luz", "la luz"),
    TEMPERATURA("temperatura", "la temperatura");

    private final String clave;
    private final String conArticulo;

    SensorDeMatera(String clave, String conArticulo) {
        this.clave = clave;
        this.conArticulo = conArticulo;
    }

    /** El nombre en el JSON. */
    public String clave() { return clave; }

    /** "la temperatura": lo que completa la frase "no te siento ...". */
    public String conArticulo() { return conArticulo; }

    public static Optional<SensorDeMatera> deClave(String clave) {
        return Arrays.stream(values()).filter(s -> s.clave.equals(clave)).findFirst();
    }
}
