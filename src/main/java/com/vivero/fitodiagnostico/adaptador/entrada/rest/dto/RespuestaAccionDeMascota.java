package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.mascota.modelo.AccionDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;

import java.time.Instant;
import java.util.UUID;

/**
 * Una acción del dueño. ACARICIAR: {@code estado=HECHA}, {@code id} del evento, {@code venceEn} nulo. LA_REGUE:
 * {@code PENDIENTE} hasta {@code venceEn}, o {@code CONFIRMADA}/{@code DESMENTIDA} con {@code resueltaEn}.
 */
public record RespuestaAccionDeMascota(UUID id, String tipo, String estado, Instant creadaEn, Instant venceEn,
                                       Instant resueltaEn) {

    public static RespuestaAccionDeMascota desde(ResultadoDeAccion r) {
        return new RespuestaAccionDeMascota(r.id(), r.tipo().name(), r.estado().name(), r.creadaEn(), r.venceEn(), null);
    }

    public static RespuestaAccionDeMascota desde(AccionDeMascota a) {
        return new RespuestaAccionDeMascota(a.id(), a.tipo().name(), a.estado().name(), a.creadaEn(), a.venceEn(),
                a.resueltaEn());
    }
}
