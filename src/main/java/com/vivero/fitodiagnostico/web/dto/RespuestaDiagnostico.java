package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Forma exacta de la respuesta 200 (sección 2 del contrato). */
public record RespuestaDiagnostico(
        Especie especie,
        Lectura lectura,
        String estado,
        String detalle,
        List<Parametro> parametros,
        Instant evaluadoEn) {

    public record Especie(String nombreCientifico, String nombreComun) { }

    public record Lectura(double temperaturaC, double humedadRelativa, int luzLux) { }

    /** Forma del anexo A: un parámetro clasificado, con la magnitud ya traducida a texto de presentación. */
    public record Parametro(String nombre, double valor, String unidad, double[] rangoOptimo, String estado) { }

    public static RespuestaDiagnostico desde(Diagnostico diagnostico) {
        return new RespuestaDiagnostico(
                new Especie(
                        diagnostico.especie().nombreCientifico(),
                        diagnostico.especie().nombreComun()),
                new Lectura(
                        diagnostico.medicion().valor(Magnitud.TEMPERATURA),
                        diagnostico.medicion().valor(Magnitud.HUMEDAD),
                        (int) diagnostico.medicion().valor(Magnitud.LUZ)),
                diagnostico.estado(),
                diagnostico.detalle(),
                diagnostico.parametros().stream().map(RespuestaDiagnostico::parametroDesde).toList(),
                diagnostico.evaluadoEn());
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
