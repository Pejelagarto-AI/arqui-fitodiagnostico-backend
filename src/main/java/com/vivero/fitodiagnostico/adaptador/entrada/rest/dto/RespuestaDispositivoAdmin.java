package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.time.Instant;
import java.util.UUID;

/** Un dispositivo visto por el administrador. Nunca incluye el secreto. */
public record RespuestaDispositivoAdmin(String serial, String estado, UUID plantaId, Instant reclamadoEn,
                                        Instant ultimaCredencialEn, Instant creadoEn,
                                        String registradoPor) {

    public static RespuestaDispositivoAdmin desde(Dispositivo d) {
        return new RespuestaDispositivoAdmin(d.serial(), d.estaReclamado() ? "RECLAMADO" : "SIN_RECLAMAR",
                d.plantaId().orElse(null), d.reclamadoEn().orElse(null), d.ultimaCredencialEn().orElse(null),
                d.creadoEn(), d.registradoPor().name());
    }
}
