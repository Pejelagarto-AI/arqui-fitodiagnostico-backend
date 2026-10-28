package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
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
 */
public record RespuestaLectura(UUID id, UUID plantaId, String origen, Instant tomadaEn, Instant registradoEn,
                               Diagnostico diagnostico) {

    public record Diagnostico(String especie, String estado, List<Parametro> parametros,
                              List<String> recomendaciones) { }

    public record Parametro(String nombre, double valor, String unidad, double[] rangoOptimo, String estado) { }

    public static RespuestaLectura desde(DiagnosticoDePlanta registro) {
        var diagnostico = registro.diagnostico();
        return new RespuestaLectura(registro.id(), registro.plantaId(), registro.origen().name(),
                registro.tomadaEn(), registro.registradoEn(),
                new Diagnostico(diagnostico.especie().nombre(), diagnostico.estado().name(),
                        diagnostico.parametros().stream().map(RespuestaLectura::parametroDesde).toList(),
                        diagnostico.recomendaciones()));
    }

    private static Parametro parametroDesde(ResultadoParametro resultado) {
        Magnitud magnitud = resultado.magnitud();
        return new Parametro(magnitud.name().toLowerCase(Locale.ROOT), resultado.valor(), magnitud.unidad(),
                new double[] { resultado.rangoOptimo().minimo(), resultado.rangoOptimo().maximo() },
                resultado.clasificacion().name());
    }
}
