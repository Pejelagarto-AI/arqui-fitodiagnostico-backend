package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * La credencial de un dispositivo (la matera con su ESP32): un texto opaco que
 * lo autoriza a reportar lecturas de UNA planta y nada más. Como {@link TokenDeSesion},
 * el dominio no sabe que es un JWT; solo conoce qué dispositivo, de qué planta y hasta cuándo.
 */
public record TokenDeDispositivo(String dispositivoId, UUID plantaId, String valor, Instant expiraEn) {

    /** Identificador que elige el dueño ("esp32-01"): corto y sin caracteres que compliquen rutas ni logs. */
    public static final Pattern FORMATO_DEL_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public TokenDeDispositivo {
        if (dispositivoId == null || !FORMATO_DEL_ID.matcher(dispositivoId).matches()) {
            throw new IllegalArgumentException(
                    "el identificador del dispositivo debe tener de 1 a 64 letras, números, punto, guion o guion bajo");
        }
        if (plantaId == null) {
            throw new IllegalArgumentException("el token de dispositivo requiere planta");
        }
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("el token requiere valor");
        }
        if (expiraEn == null) {
            throw new IllegalArgumentException("el token requiere caducidad");
        }
    }

    @Override
    public String toString() {
        return "TokenDeDispositivo[dispositivoId=" + dispositivoId + ", plantaId=" + plantaId + ", expiraEn=" + expiraEn + "]";
    }
}
