package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * El diagnóstico que queda en el historial tras reportar una lectura. Mismas claves que la respuesta de
 * {@code POST /api/v1/plantas/{id}/mediciones}, para que quien lea ambas vea lo mismo; se redefine aquí y
 * no se reutiliza porque un adaptador de entrada no depende de otro.
 *
 * <p>Fase 8: agrega {@code periodo} (DIA o NOCHE, para que la OLED duerma la planta),
 * {@code exposicionLuminica} y, por parámetro, {@code valorEvaluado}. No cambia ningún campo existente.
 */
public record RespuestaLectura(UUID id, UUID plantaId, String origen, Instant tomadaEn, Instant registradoEn,
                               Diagnostico diagnostico, String apodo) {

    public record Diagnostico(String especie, String estado, List<Parametro> parametros,
                              List<String> recomendaciones, String periodo, Exposicion exposicionLuminica) { }

    public record Parametro(String nombre, double valor, String unidad, double[] rangoOptimo, String estado,
                            double valorEvaluado) { }

    public record Exposicion(String base, Double horasObservadas, Double horasDeLuz, Double luxPromedioEnLuz,
                             Double luxEfectivo, Double horasEnOscuridad) { }

    public static RespuestaLectura desde(DiagnosticoDePlanta registro, String apodo) {
        var diagnostico = registro.diagnostico();
        return new RespuestaLectura(registro.id(), registro.plantaId(), registro.origen().name(),
                registro.tomadaEn(), registro.registradoEn(),
                new Diagnostico(diagnostico.especie().nombre(), diagnostico.estado().name(),
                        diagnostico.parametros().stream().map(RespuestaLectura::parametroDesde).toList(),
                        diagnostico.recomendaciones(), diagnostico.periodo().name(),
                        exposicionDesde(diagnostico)), apodo);
    }

    private static Exposicion exposicionDesde(com.vivero.fitodiagnostico.dominio.modelo.Diagnostico diagnostico) {
        ExposicionLuminica e = diagnostico.exposicion();
        String base = diagnostico.baseDeLuz().name();
        if (e == null) {
            return new Exposicion(base, null, null, null, null, null);
        }
        return new Exposicion(base, redondear(e.horasObservadas(), 100), redondear(e.horasDeLuz(), 100),
                redondear(e.luxPromedioEnLuz(), 10), redondear(e.luxEfectivo(), 10),
                redondear(e.horasEnOscuridad(), 100));
    }

    private static double redondear(double valor, double escala) {
        return Math.round(valor * escala) / escala;
    }

    private static Parametro parametroDesde(ResultadoParametro resultado) {
        Magnitud magnitud = resultado.magnitud();
        return new Parametro(magnitud.name().toLowerCase(Locale.ROOT), resultado.valor(), magnitud.unidad(),
                new double[] { resultado.rangoOptimo().minimo(), resultado.rangoOptimo().maximo() },
                resultado.clasificacion().name(), resultado.valorEvaluado());
    }
}
