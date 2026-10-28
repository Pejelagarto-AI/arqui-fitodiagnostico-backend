package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorTokenDispositivoPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Doble del emisor de tokens de dispositivo: sin firma, sin JWT; solo recuerda lo que le pidieron. */
public class EmisorTokenDispositivoFijo implements EmisorTokenDispositivoPort {

    public static final Instant CADUCIDAD = Instant.parse("2027-01-01T00:00:00Z");

    private final List<TokenDeDispositivo> emitidos = new ArrayList<>();

    @Override
    public TokenDeDispositivo emitir(String dispositivoId, UUID plantaId) {
        TokenDeDispositivo token = new TokenDeDispositivo(dispositivoId, plantaId,
                "token-falso-de-" + dispositivoId, CADUCIDAD);
        emitidos.add(token);
        return token;
    }

    public List<TokenDeDispositivo> emitidos() {
        return List.copyOf(emitidos);
    }
}
