package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.AutenticarDispositivoPort.CredencialEmitida;

import java.time.Instant;
import java.util.UUID;

/** La credencial del dispositivo: el token que manda en {@code Authorization: Bearer} y cuándo caduca. */
public record RespuestaCredencial(String token, UUID plantaId, Instant expira, String apodo) {

    static RespuestaCredencial desde(CredencialEmitida c) {
        TokenDeDispositivo t = c.token();
        return new RespuestaCredencial(t.valor(), t.plantaId(), t.expiraEn(), c.apodo());
    }
}
