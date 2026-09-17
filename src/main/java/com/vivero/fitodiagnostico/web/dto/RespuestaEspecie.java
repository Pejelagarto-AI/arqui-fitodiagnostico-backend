package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Forma de un elemento de la respuesta de GET /api/v1/especies. */
public record RespuestaEspecie(String nombre, Map<String, Rango> rangos) {

    public record Rango(double min, double max, String unidad) { }

    public static RespuestaEspecie desde(Especie especie) {
        Map<String, Rango> rangos = new LinkedHashMap<>();
        for (Magnitud magnitud : Magnitud.values()) {
            com.vivero.fitodiagnostico.dominio.modelo.Rango rango = especie.rango(magnitud);
            rangos.put(claveDePresentacion(magnitud), new Rango(rango.minimo(), rango.maximo(), magnitud.unidad()));
        }
        return new RespuestaEspecie(especie.nombre(), rangos);
    }

    /** Traduce la magnitud a su clave de presentación en minúscula; es presentación, no dominio. */
    private static String claveDePresentacion(Magnitud magnitud) {
        return magnitud.name().toLowerCase(Locale.ROOT);
    }
}
