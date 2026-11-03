package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;

/**
 * El desafío que el servidor le plantea a un dispositivo para que demuestre que conoce su secreto:
 * un texto aleatorio de un solo uso con caducidad corta. El dominio no sabe cómo se responde
 * (HMAC u otra cosa); eso lo decide el puerto {@code AutenticadorDeDispositivos}.
 */
public record Reto(String valor, Instant expira) {

    public Reto {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("el reto requiere valor");
        }
        if (expira == null) {
            throw new IllegalArgumentException("el reto requiere caducidad");
        }
    }

    /** Vigente hasta el instante de caducidad, sin incluirlo. */
    public boolean vigenteEn(Instant ahora) {
        return ahora.isBefore(expira);
    }
}
