package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;

import java.time.Instant;
import java.util.UUID;

/** La credencial del dispositivo: el token que manda en {@code Authorization: Bearer} y cuándo caduca. */
public record RespuestaCredencial(String token, UUID plantaId, Instant expira) {

    static RespuestaCredencial desde(TokenDeDispositivo t) {
        return new RespuestaCredencial(t.valor(), t.plantaId(), t.expiraEn());
    }
}
