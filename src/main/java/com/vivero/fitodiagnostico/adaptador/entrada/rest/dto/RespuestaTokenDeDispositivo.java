package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;

import java.time.Instant;
import java.util.UUID;

/** La credencial que el dueño copia al dispositivo. Es la única vez que se muestra: no se guarda. */
public record RespuestaTokenDeDispositivo(String dispositivoId, UUID plantaId, String token, Instant expira) {

    public static RespuestaTokenDeDispositivo desde(TokenDeDispositivo token) {
        return new RespuestaTokenDeDispositivo(token.dispositivoId(), token.plantaId(), token.valor(), token.expiraEn());
    }
}
