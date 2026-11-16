package com.vivero.fitodiagnostico.salud;

import java.util.Set;
import java.util.UUID;

/** Puerto de salida: empuja a quien mira la planta que la salud de su matera cambió (hoy, por WebSocket). */
public interface AvisadorDeSalud {

    record Aviso(UUID plantaId, Set<UUID> destinatarios, SaludDelDispositivo salud, String mensaje) { }

    void avisar(Aviso aviso);
}
