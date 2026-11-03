package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.time.Instant;
import java.util.UUID;

/** Respuesta de reclamar: el dispositivo ya es de la planta. No hay credencial que mostrar. */
public record RespuestaReclamoDeDispositivo(String serial, UUID plantaId, Instant reclamadoEn) {

    public static RespuestaReclamoDeDispositivo desde(Dispositivo d) {
        return new RespuestaReclamoDeDispositivo(d.serial(), d.plantaId().orElseThrow(), d.reclamadoEn().orElseThrow());
    }
}
