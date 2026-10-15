package com.vivero.fitodiagnostico.dobles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba que solo avanza cuando la prueba lo pide: para probar caducidades sin esperar. */
public class RelojManual extends Clock {

    private Instant ahora;

    public RelojManual(Instant inicio) {
        this.ahora = inicio;
    }

    public static RelojManual enUnDiaCualquiera() {
        return new RelojManual(Instant.parse("2026-10-14T15:00:00Z"));
    }

    public void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
