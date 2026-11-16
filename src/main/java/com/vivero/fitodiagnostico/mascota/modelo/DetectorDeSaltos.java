package com.vivero.fitodiagnostico.mascota.modelo;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

/**
 * Detecta REGADA y SALIO_AL_SOL en las lecturas recientes (D134, D135). Una sola regla con dos umbrales:
 *
 * <ol>
 *   <li>Las lecturas se ordenan por la hora en que se tomaron. La última es la <b>confirmación</b>, la penúltima la
 *       <b>candidata</b>, y la <b>referencia</b> son las anteriores a la candidata dentro de la ventana (una hora).</li>
 *   <li>Umbral = f(mediana de la referencia). Riego: mediana + 15 puntos. Sol: el mayor entre mediana x 2 y
 *       mediana + 2000 lux.</li>
 *   <li>Hay evento si la candidata Y la confirmación superan el umbral y la lectura justo antes de la candidata no
 *       (es el borde de subida, no la meseta de después), y pasó el enfriamiento desde el anterior del mismo tipo.</li>
 * </ol>
 *
 * Una sola lectura alta nunca dispara: hace falta la siguiente. Un pico que sube y baja se ignora porque la
 * confirmación queda bajo el umbral. Con menos de {@code muestrasMinimasDeReferencia} lecturas en la ventana no hay
 * con qué comparar y no se detecta nada.
 */
public final class DetectorDeSaltos {

    private final ParametrosDeMascota parametros;

    public DetectorDeSaltos(ParametrosDeMascota parametros) {
        this.parametros = parametros;
    }

    /** REGADA si la humedad del sustrato saltó y se sostuvo. */
    public Optional<EventoDeMascota> riego(UUID plantaId, List<LecturaReciente> muestras, Instant ultimaRegadaEn) {
        return detectar(muestras, LecturaReciente::humedad, mediana -> mediana + parametros.subidaMinimaDeRiego(),
                ultimaRegadaEn)
                .map(s -> EventoDeMascota.nuevo(plantaId, TipoDeEvento.REGADA, s.candidata().tomadaEn(),
                        detalle(s, "humedad")));
    }

    /**
     * SALIO_AL_SOL si la luz saltó y se sostuvo. Día/noche: el diagnóstico ya trae {@code periodo} (fase 8, D121), pero
     * {@link LecturaReciente} todavía no lo copia. Al consumirlo, aquí se descarta el salto del amanecer: un salto con periodo DIA en la candidata y NOCHE
     * en la referencia es la mañana, no la matera que alguien sacó al balcón.
     */
    public Optional<EventoDeMascota> sol(UUID plantaId, List<LecturaReciente> muestras, Instant ultimoSolEn) {
        return detectar(muestras, LecturaReciente::luz,
                mediana -> Math.max(mediana * parametros.factorDeSol(), mediana + parametros.subidaMinimaDeSol()),
                ultimoSolEn)
                .map(s -> EventoDeMascota.nuevo(plantaId, TipoDeEvento.SALIO_AL_SOL, s.candidata().tomadaEn(),
                        detalle(s, "luz")));
    }

    /** La mediana de humedad de la última ventana antes de {@code hasta}, si hay lecturas en ella. */
    public Optional<Double> humedadDeReferencia(List<LecturaReciente> muestras, Instant hasta) {
        Instant desde = hasta.minus(parametros.ventanaDeReferencia());
        List<Double> valores = muestras.stream()
                .filter(m -> !m.tomadaEn().isBefore(desde) && !m.tomadaEn().isAfter(hasta))
                .map(LecturaReciente::humedad).toList();
        return valores.isEmpty() ? Optional.empty() : Optional.of(Mediana.de(valores));
    }

    private record Salto(LecturaReciente candidata, LecturaReciente confirmacion, double referencia, double umbral,
                         ToDoubleFunction<LecturaReciente> valor) { }

    private Optional<Salto> detectar(List<LecturaReciente> muestras, ToDoubleFunction<LecturaReciente> valor,
                                     DoubleUnaryOperator umbralSobre, Instant ultimoDelMismoTipo) {
        List<LecturaReciente> ordenadas = muestras.stream().sorted(Comparator.comparing(LecturaReciente::tomadaEn)).toList();
        int n = ordenadas.size();
        if (n < parametros.muestrasMinimasDeReferencia() + 2) {
            return Optional.empty();
        }
        LecturaReciente confirmacion = ordenadas.get(n - 1);
        LecturaReciente candidata = ordenadas.get(n - 2);
        LecturaReciente previa = ordenadas.get(n - 3);
        Instant desde = candidata.tomadaEn().minus(parametros.ventanaDeReferencia());
        List<Double> referencia = ordenadas.subList(0, n - 2).stream()
                .filter(m -> !m.tomadaEn().isBefore(desde))
                .map(valor::applyAsDouble).toList();
        if (referencia.size() < parametros.muestrasMinimasDeReferencia()) {
            return Optional.empty();
        }
        double mediana = Mediana.de(referencia);
        double umbral = umbralSobre.applyAsDouble(mediana);
        boolean salto = valor.applyAsDouble(candidata) >= umbral && valor.applyAsDouble(confirmacion) >= umbral
                && valor.applyAsDouble(previa) < umbral;
        boolean enfriado = ultimoDelMismoTipo == null
                || !Duration.between(ultimoDelMismoTipo, candidata.tomadaEn()).minus(parametros.enfriamientoDeEventos())
                        .isNegative();
        return salto && enfriado
                ? Optional.of(new Salto(candidata, confirmacion, mediana, umbral, valor))
                : Optional.empty();
    }

    private static Map<String, String> detalle(Salto s, String magnitud) {
        Map<String, String> detalle = new LinkedHashMap<>();
        detalle.put("magnitud", magnitud);
        detalle.put("referencia", redondear(s.referencia()));
        detalle.put("umbral", redondear(s.umbral()));
        detalle.put("valor", redondear(s.valor().applyAsDouble(s.candidata())));
        detalle.put("confirmacion", redondear(s.valor().applyAsDouble(s.confirmacion())));
        detalle.put("confirmadaEn", s.confirmacion().tomadaEn().toString());
        return detalle;
    }

    static String redondear(double valor) {
        return String.valueOf(Math.round(valor * 10) / 10.0);
    }
}
