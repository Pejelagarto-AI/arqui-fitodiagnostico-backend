package com.vivero.fitodiagnostico.mascota.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Una declaración "la regué" (D138). Se guarda PENDIENTE con la humedad de referencia del momento y un plazo; el
 * sensor la confirma o, al vencer, la desmiente. {@code humedadDeReferencia} es nula si la planta no tenía lecturas.
 */
public record AccionDeMascota(UUID id, UUID plantaId, TipoDeAccion tipo, EstadoDeAccion estado, Instant creadaEn,
                              Instant venceEn, Instant resueltaEn, Double humedadDeReferencia) {

    public AccionDeMascota {
        if (id == null || plantaId == null || tipo == null || estado == null || creadaEn == null) {
            throw new IllegalArgumentException("la acción de mascota requiere id, planta, tipo, estado y fecha");
        }
    }

    public static AccionDeMascota riegoDeclarado(UUID plantaId, Instant ahora, Instant venceEn,
                                                 Double humedadDeReferencia) {
        return new AccionDeMascota(UUID.randomUUID(), plantaId, TipoDeAccion.LA_REGUE, EstadoDeAccion.PENDIENTE,
                ahora, venceEn, null, humedadDeReferencia);
    }

    public boolean pendiente() {
        return estado == EstadoDeAccion.PENDIENTE;
    }

    public AccionDeMascota resuelta(EstadoDeAccion fin, Instant en) {
        return new AccionDeMascota(id, plantaId, tipo, fin, creadaEn, venceEn, en, humedadDeReferencia);
    }
}
