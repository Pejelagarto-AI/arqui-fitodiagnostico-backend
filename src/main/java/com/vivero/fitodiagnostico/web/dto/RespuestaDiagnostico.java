package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import java.util.List;
import java.util.Locale;

/**
 * Forma exacta de la respuesta 200 (sección 2 del contrato del Anexo A). Sin
 * {@code lectura} ni {@code evaluadoEn}: el front solo necesita la especie,
 * el estado agregado, los parámetros clasificados y las recomendaciones.
 */
public record RespuestaDiagnostico(
        String especie,
        String estado,
        List<Parametro> parametros,
        List<String> recomendaciones) {

    /** Forma del anexo A: un parámetro clasificado, con la magnitud ya traducida a texto de presentación. */
    public record Parametro(String nombre, double valor, String unidad, double[] rangoOptimo, String estado) { }

    public static RespuestaDiagnostico desde(Diagnostico diagnostico) {
        return new RespuestaDiagnostico(
                diagnostico.especie().nombre(),
                diagnostico.estado().name(),
                diagnostico.parametros().stream().map(RespuestaDiagnostico::parametroDesde).toList(),
                diagnostico.recomendaciones());
    }

    private static Parametro parametroDesde(ResultadoParametro resultado) {
        return new Parametro(
                nombreDePresentacion(resultado.magnitud()),
                resultado.valor(),
                resultado.magnitud().unidad(),
                new double[] { resultado.rangoOptimo().minimo(), resultado.rangoOptimo().maximo() },
                resultado.clasificacion().name());
    }

    /** Traduce la magnitud a su nombre de presentación en minúscula; es presentación, no dominio. */
    private static String nombreDePresentacion(Magnitud magnitud) {
        return magnitud.name().toLowerCase(Locale.ROOT);
    }
}
