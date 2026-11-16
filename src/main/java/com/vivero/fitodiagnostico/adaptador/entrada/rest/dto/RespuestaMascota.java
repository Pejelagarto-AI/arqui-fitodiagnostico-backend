package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.mascota.modelo.MapaDeAnimo;
import com.vivero.fitodiagnostico.mascota.modelo.VistaDeMascota;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * GET /api/v1/plantas/{id}/mascota. {@code causa} es nula cuando el ánimo no viene de un parámetro fuera de rango;
 * {@code accionPendiente} es nula si no hay un "la regué" esperando al sensor.
 */
public record RespuestaMascota(UUID plantaId, boolean hayLecturas, int vitalidad, boolean desmayada, String animo,
                               Causa causa, String estadoDelDiagnostico, Racha racha, Instant actualizadaEn,
                               List<RespuestaEventoDeMascota> ultimosEventos,
                               RespuestaAccionDeMascota accionPendiente) {

    /** El parámetro que explica el ánimo: nombre en minúsculas (como en el diagnóstico), BAJO o ALTO, y cuánto. */
    public record Causa(String parametro, String direccion, double desviacion) {
        static Causa desde(MapaDeAnimo.Causa c) {
            return new Causa(c.magnitud().name().toLowerCase(Locale.ROOT), c.direccion().name(),
                    Math.round(c.desviacion() * 100) / 100.0);
        }
    }

    public record Racha(int dias, Instant desde) { }

    public static RespuestaMascota desde(VistaDeMascota v) {
        return new RespuestaMascota(v.plantaId(), v.hayLecturas(), v.vitalidad(), v.desmayada(), v.animo().name(),
                v.causa().map(Causa::desde).orElse(null),
                v.estadoDelDiagnostico() == null ? null : v.estadoDelDiagnostico().name(),
                new Racha(v.rachaDias(), v.rachaDesde()), v.actualizadaEn(),
                v.ultimosEventos().stream().map(RespuestaEventoDeMascota::desde).toList(),
                v.accionPendiente().map(RespuestaAccionDeMascota::desde).orElse(null));
    }
}
