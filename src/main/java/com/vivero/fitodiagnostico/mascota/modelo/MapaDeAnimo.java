package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;

import java.util.Comparator;
import java.util.Optional;

/**
 * Del último diagnóstico y la vitalidad, al ánimo (D136). Un mapa de presentación: el dominio dice "humedad BAJO a
 * 0,42 del ancho del rango"; la mascota lo traduce a SEDIENTA. Si fallan varios parámetros gana el que más se
 * desvía (la desviación relativa del dominio); a igual desviación, el orden de {@link Magnitud}.
 *
 * <p>Fase 12 (D170): de noche ({@code Diagnostico.periodo() == NOCHE}) la luz no puede producir A_OSCURAS ni
 * DESLUMBRADA: se ignora al buscar el parámetro que más se desvía y, si era lo único que fallaba, el ánimo es DORMIDA
 * (sin {@code causa}: dormir no es un problema). Si de noche falla otra magnitud, gana esa.
 *
 * <p>Precedencia: DESMAYADA (vitalidad en 0) gana sobre todo; sin diagnóstico, SIN_DATOS; con todo en rango,
 * FELIZ, o CANSADA si la vitalidad sigue baja.
 */
public final class MapaDeAnimo {

    /** El parámetro que explica el ánimo: magnitud, dirección y cuánto se sale del rango (fracción del ancho). */
    public record Causa(Magnitud magnitud, Clasificacion direccion, double desviacion) { }

    /** El ánimo y, si viene de un parámetro fuera de rango, cuál. */
    public record Resultado(Animo animo, Optional<Causa> causa) { }

    private final ParametrosDeMascota parametros;

    public MapaDeAnimo(ParametrosDeMascota parametros) {
        this.parametros = parametros;
    }

    public Resultado de(Optional<Diagnostico> ultimo, Optional<EstadoDeMascota> estado) {
        if (estado.map(EstadoDeMascota::desmayada).orElse(false)) {
            return new Resultado(Animo.DESMAYADA, ultimo.flatMap(MapaDeAnimo::peor));
        }
        if (ultimo.isEmpty()) {
            return new Resultado(Animo.SIN_DATOS, Optional.empty());
        }
        Optional<Causa> causa = peor(ultimo.get());
        if (causa.isEmpty()) {
            if (soloFallaLaLuzDeNoche(ultimo.get())) {
                return new Resultado(Animo.DORMIDA, Optional.empty());
            }
            double vitalidad = estado.map(EstadoDeMascota::vitalidad).orElse(parametros.vitalidadInicial());
            return new Resultado(vitalidad < parametros.vitalidadCansada() ? Animo.CANSADA : Animo.FELIZ,
                    Optional.empty());
        }
        return new Resultado(animoDe(causa.get()), causa);
    }

    static Animo animoDe(Causa causa) {
        boolean bajo = causa.direccion() == Clasificacion.BAJO;
        return switch (causa.magnitud()) {
            case HUMEDAD -> bajo ? Animo.SEDIENTA : Animo.AHOGADA;
            case TEMPERATURA -> bajo ? Animo.CON_FRIO : Animo.CON_CALOR;
            // De noche la luz ya no llega aquí (peor() la descarta y el ánimo es DORMIDA, D170).
            case LUZ -> bajo ? Animo.A_OSCURAS : Animo.DESLUMBRADA;
        };
    }

    /** De noche, la luz fuera de rango y ningún otro parámetro: la planta duerme. */
    private static boolean soloFallaLaLuzDeNoche(Diagnostico diagnostico) {
        return diagnostico.periodo() == Periodo.NOCHE
                && diagnostico.parametros().stream()
                        .anyMatch(p -> p.magnitud() == Magnitud.LUZ && p.fueraDeRango());
    }

    private static Optional<Causa> peor(Diagnostico diagnostico) {
        boolean deNoche = diagnostico.periodo() == Periodo.NOCHE;
        return diagnostico.parametros().stream()
                .filter(r -> !(deNoche && r.magnitud() == Magnitud.LUZ))
                .filter(ResultadoParametro::fueraDeRango)
                .max(Comparator.comparingDouble(ResultadoParametro::desviacionRelativa)
                        .thenComparing(r -> -r.magnitud().ordinal()))
                .map(r -> new Causa(r.magnitud(), r.clasificacion(), r.desviacionRelativa()));
    }
}
