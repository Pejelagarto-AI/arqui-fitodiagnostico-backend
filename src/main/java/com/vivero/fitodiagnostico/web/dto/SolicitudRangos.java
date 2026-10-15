package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cuerpo de editar los rangos de una especie (RF-C4), con la misma forma de
 * {@code rangos} que devuelve {@code GET /api/v1/especies}: lo que el front
 * lee es lo que puede reenviar. El borde convierte las claves de presentación
 * ({@code "humedad"}) a {@link Magnitud} (RA6); que estén todas y quepan en el
 * sensor lo exige el servicio.
 */
public record SolicitudRangos(

        @NotNull
        Map<String, @NotNull @Valid RangoSolicitud> rangos) {

    public record RangoSolicitud(@NotNull Double min, @NotNull Double max) { }

    /** @throws IllegalArgumentException magnitud desconocida o rango invertido (se responde 400) */
    public Map<Magnitud, Rango> rangosDeDominio() {
        Map<Magnitud, Rango> resultado = new EnumMap<>(Magnitud.class);
        rangos.forEach((clave, rango) -> resultado.put(magnitudDe(clave), new Rango(rango.min(), rango.max())));
        return resultado;
    }

    private static Magnitud magnitudDe(String clave) {
        for (Magnitud magnitud : Magnitud.values()) {
            if (magnitud.name().toLowerCase(Locale.ROOT).equals(clave)) {
                return magnitud;
            }
        }
        throw new IllegalArgumentException("magnitud desconocida: '" + clave + "'");
    }
}
