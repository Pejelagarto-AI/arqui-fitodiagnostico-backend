package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Set;
import java.util.UUID;

/**
 * Qué se avisa, a quién y con qué texto. No dice POR QUÉ CANAL: eso lo decide
 * cada adaptador de {@code Notificador} (log, correo, WebSocket).
 */
public record Aviso(Set<UUID> destinatarios, TipoDeAviso tipo, String mensaje) {

    public Aviso {
        if (tipo == null) {
            throw new IllegalArgumentException("el aviso requiere tipo");
        }
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("el aviso requiere mensaje");
        }
        destinatarios = destinatarios == null ? Set.of() : Set.copyOf(destinatarios);
    }
}
