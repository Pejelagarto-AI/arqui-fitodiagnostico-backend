package com.vivero.fitodiagnostico.mascota.modelo;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decide si el sensor confirma o desmiente un "la regué" pendiente (D138). Dos caminos para confirmar:
 *
 * <ol>
 *   <li>hubo una REGADA entre {@code plazo} antes de la declaración y el vencimiento (el dueño suele avisar
 *       después de regar);</li>
 *   <li>dos lecturas seguidas tomadas después de la declaración superan la humedad de referencia (la mediana de la
 *       última hora al declarar) en {@code subidaMinimaDeRiego}. Cubre el caso en que la REGADA no se emitió por el
 *       enfriamiento.</li>
 * </ol>
 *
 * Al vencer sin ninguna de las dos, DESMENTIDA, con el motivo: no llegaron lecturas o la humedad no subió.
 */
public final class ConfirmadorDeRiego {

    /** Cómo terminó y el evento que lo cuenta. */
    public record Resolucion(AccionDeMascota accion, EventoDeMascota evento) { }

    private final ParametrosDeMascota parametros;

    public ConfirmadorDeRiego(ParametrosDeMascota parametros) {
        this.parametros = parametros;
    }

    public Optional<Resolucion> evaluar(AccionDeMascota pendiente, List<Muestra> muestras, Instant ultimaRegadaEn,
                                        Instant ahora) {
        if (!pendiente.pendiente()) {
            return Optional.empty();
        }
        Instant desde = pendiente.creadaEn().minus(parametros.plazoParaConfirmarRiego());
        if (ultimaRegadaEn != null && !ultimaRegadaEn.isBefore(desde) && !ultimaRegadaEn.isAfter(pendiente.venceEn())) {
            return Optional.of(resolver(pendiente, EstadoDeAccion.CONFIRMADA, ahora,
                    Map.of("motivo", "REGADA_DETECTADA", "regadaEn", ultimaRegadaEn.toString())));
        }
        List<Muestra> posteriores = muestras.stream()
                .filter(m -> m.tomadaEn().isAfter(pendiente.creadaEn()) && !m.tomadaEn().isAfter(pendiente.venceEn()))
                .sorted(Comparator.comparing(Muestra::tomadaEn)).toList();
        if (pendiente.humedadDeReferencia() != null) {
            double umbral = pendiente.humedadDeReferencia() + parametros.subidaMinimaDeRiego();
            for (int i = 1; i < posteriores.size(); i++) {
                if (posteriores.get(i - 1).humedad() >= umbral && posteriores.get(i).humedad() >= umbral) {
                    Map<String, String> detalle = new LinkedHashMap<>();
                    detalle.put("motivo", "HUMEDAD_SUBIO");
                    detalle.put("referencia", DetectorDeSaltos.redondear(pendiente.humedadDeReferencia()));
                    detalle.put("humedad", DetectorDeSaltos.redondear(posteriores.get(i).humedad()));
                    return Optional.of(resolver(pendiente, EstadoDeAccion.CONFIRMADA, ahora, detalle));
                }
            }
        }
        if (!ahora.isBefore(pendiente.venceEn())) {
            return Optional.of(resolver(pendiente, EstadoDeAccion.DESMENTIDA, ahora,
                    Map.of("motivo", posteriores.isEmpty() ? "SIN_LECTURAS" : "LA_HUMEDAD_NO_SUBIO")));
        }
        return Optional.empty();
    }

    private static Resolucion resolver(AccionDeMascota pendiente, EstadoDeAccion fin, Instant ahora,
                                       Map<String, String> detalle) {
        Map<String, String> conAccion = new LinkedHashMap<>(detalle);
        conAccion.put("accionId", pendiente.id().toString());
        TipoDeEvento tipo = fin == EstadoDeAccion.CONFIRMADA ? TipoDeEvento.RIEGO_CONFIRMADO : TipoDeEvento.RIEGO_DESMENTIDO;
        return new Resolucion(pendiente.resuelta(fin, ahora),
                EventoDeMascota.nuevo(pendiente.plantaId(), tipo, ahora, conAccion));
    }
}
