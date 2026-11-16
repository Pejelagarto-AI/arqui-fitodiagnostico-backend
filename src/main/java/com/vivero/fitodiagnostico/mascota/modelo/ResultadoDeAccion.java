package com.vivero.fitodiagnostico.mascota.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta a una acción del dueño. ACARICIAR: HECHA, {@code id} es el del evento ACARICIADA y no vence. LA_REGUE:
 * PENDIENTE (o ya CONFIRMADA si el sensor había visto el riego), {@code id} es el de la acción.
 */
public record ResultadoDeAccion(UUID id, TipoDeAccion tipo, EstadoDeAccion estado, Instant creadaEn,
                                Instant venceEn, boolean repetida) {

    /**
     * Fase 12 (D172): {@code repetida} es verdadero cuando una caricia llegó antes de que pasara el enfriamiento
     * desde la anterior: no creó evento y {@code id} y {@code creadaEn} son los de la última. El controlador responde 200.
     */
    public ResultadoDeAccion(UUID id, TipoDeAccion tipo, EstadoDeAccion estado, Instant creadaEn, Instant venceEn) {
        this(id, tipo, estado, creadaEn, venceEn, false);
    }

    public static ResultadoDeAccion de(AccionDeMascota accion) {
        return new ResultadoDeAccion(accion.id(), accion.tipo(), accion.estado(), accion.creadaEn(), accion.venceEn());
    }
}
