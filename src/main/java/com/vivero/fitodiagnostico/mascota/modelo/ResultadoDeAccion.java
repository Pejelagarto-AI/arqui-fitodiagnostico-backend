package com.vivero.fitodiagnostico.mascota.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta a una acción del dueño. ACARICIAR: HECHA, {@code id} es el del evento ACARICIADA y no vence. LA_REGUE:
 * PENDIENTE (o ya CONFIRMADA si el sensor había visto el riego), {@code id} es el de la acción.
 */
public record ResultadoDeAccion(UUID id, TipoDeAccion tipo, EstadoDeAccion estado, Instant creadaEn,
                                Instant venceEn) {

    public static ResultadoDeAccion de(AccionDeMascota accion) {
        return new ResultadoDeAccion(accion.id(), accion.tipo(), accion.estado(), accion.creadaEn(), accion.venceEn());
    }
}
