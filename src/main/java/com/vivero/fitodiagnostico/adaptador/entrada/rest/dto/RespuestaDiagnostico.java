package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import java.util.List;
import java.util.Locale;

/**
 * Forma exacta de la respuesta 200 (sección 2 del contrato del Anexo A). Sin
 * {@code lectura} ni {@code evaluadoEn}: el front solo necesita la especie,
 * el estado agregado, los parámetros clasificados y las recomendaciones.
 *
 * <p>Fase 8 (D129): solo AGREGA campos, al final, y no cambia el significado de
 * ninguno. {@code periodo} es DIA o NOCHE; {@code exposicionLuminica} dice con qué
 * se juzgó la luz ({@code base}: INSTANTANEA o ACUMULADA) y, si fue acumulada, las
 * horas y lux de las últimas 24 h. En cada parámetro, {@code valor} sigue siendo la
 * lectura y {@code valorEvaluado} es lo que se comparó con el rango.
 *
 * <p>Fase 15 (D203): agrega {@code regadaEn} al final, con la misma regla: cuándo se detectó el
 * último riego de las 24 h previas (ISO-8601), o null si no hubo. Con él, una humedad ALTO es
 * "recién regada" y no "ahogada".
 */
public record RespuestaDiagnostico(
        String especie,
        String estado,
        List<Parametro> parametros,
        List<String> recomendaciones,
        String periodo,
        Exposicion exposicionLuminica,
        String regadaEn) {

    /** Forma del anexo A: un parámetro clasificado, con la magnitud ya traducida a texto de presentación. */
    public record Parametro(String nombre, double valor, String unidad, double[] rangoOptimo, String estado,
                            double valorEvaluado) { }

    /** La luz de las últimas 24 h. Con base INSTANTANEA los números van en null: no hubo historial. */
    public record Exposicion(String base, Double horasObservadas, Double horasDeLuz, Double luxPromedioEnLuz,
                             Double luxEfectivo, Double horasEnOscuridad) {

        public static Exposicion desde(Diagnostico diagnostico) {
            ExposicionLuminica e = diagnostico.exposicion();
            if (e == null) {
                return new Exposicion(diagnostico.baseDeLuz().name(), null, null, null, null, null);
            }
            return new Exposicion(diagnostico.baseDeLuz().name(), redondear(e.horasObservadas(), 100),
                    redondear(e.horasDeLuz(), 100), redondear(e.luxPromedioEnLuz(), 10),
                    redondear(e.luxEfectivo(), 10), redondear(e.horasEnOscuridad(), 100));
        }

        /** Presentación: dos decimales en horas, uno en lux. El dominio guarda el número completo. */
        private static double redondear(double valor, double escala) {
            return Math.round(valor * escala) / escala;
        }
    }

    public static RespuestaDiagnostico desde(Diagnostico diagnostico) {
        return new RespuestaDiagnostico(
                diagnostico.especie().nombre(),
                diagnostico.estado().name(),
                diagnostico.parametros().stream().map(RespuestaDiagnostico::parametroDesde).toList(),
                diagnostico.recomendaciones(),
                diagnostico.periodo().name(),
                Exposicion.desde(diagnostico),
                diagnostico.regadaEn() == null ? null : diagnostico.regadaEn().toString());
    }

    private static Parametro parametroDesde(ResultadoParametro resultado) {
        return new Parametro(
                nombreDePresentacion(resultado.magnitud()),
                resultado.valor(),
                resultado.magnitud().unidad(),
                new double[] { resultado.rangoOptimo().minimo(), resultado.rangoOptimo().maximo() },
                resultado.clasificacion().name(),
                resultado.valorEvaluado());
    }

    /** Traduce la magnitud a su nombre de presentación en minúscula; es presentación, no dominio. */
    private static String nombreDePresentacion(Magnitud magnitud) {
        return magnitud.name().toLowerCase(Locale.ROOT);
    }
}
