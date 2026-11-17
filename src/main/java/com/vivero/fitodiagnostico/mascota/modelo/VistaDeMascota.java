package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que devuelve la consulta de la mascota (GET .../mascota). Sin lecturas, {@code hayLecturas} es falso, la
 * vitalidad es la inicial y el ánimo SIN_DATOS: no es un error, igual que el diagnóstico vigente (RN9).
 */
public record VistaDeMascota(UUID plantaId, boolean hayLecturas, int vitalidad, boolean desmayada, Animo animo,
                             Optional<MapaDeAnimo.Causa> causa, EstadoGlobal estadoDelDiagnostico, int rachaDias,
                             Instant rachaDesde, Instant actualizadaEn, List<EventoDeMascota> ultimosEventos,
                             Optional<AccionDeMascota> accionPendiente, Accesorio accesorio) {

    public VistaDeMascota {
        ultimosEventos = List.copyOf(ultimosEventos);
    }
}
