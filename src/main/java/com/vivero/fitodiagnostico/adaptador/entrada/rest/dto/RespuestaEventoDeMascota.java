package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Un evento de la línea de tiempo de la mascota. */
public record RespuestaEventoDeMascota(UUID id, String tipo, Instant ocurridoEn, Map<String, String> detalle) {

    public static RespuestaEventoDeMascota desde(EventoDeMascota evento) {
        return new RespuestaEventoDeMascota(evento.id(), evento.tipo().name(), evento.ocurridoEn(), evento.detalle());
    }
}
