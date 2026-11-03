package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.modelo.Reto;

import java.time.Instant;

/** El desafío: 32 bytes aleatorios en base64 estándar, de un solo uso, vigentes hasta {@code expira}. */
public record RespuestaReto(String reto, Instant expira) {

    static RespuestaReto desde(Reto r) {
        return new RespuestaReto(r.valor(), r.expira());
    }
}
