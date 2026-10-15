package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;

/**
 * Prueba de sesión que recibe el cliente al iniciar sesión (RF-A2). El dominio
 * solo sabe que es un texto opaco con caducidad; que sea un JWT es un detalle
 * del adaptador que lo emite.
 */
public record TokenDeSesion(String valor, Instant expiraEn) {

    public TokenDeSesion {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("el token requiere valor");
        }
        if (expiraEn == null) {
            throw new IllegalArgumentException("el token requiere caducidad");
        }
    }

    @Override
    public String toString() {
        return "TokenDeSesion[expiraEn=" + expiraEn + "]";
    }
}
