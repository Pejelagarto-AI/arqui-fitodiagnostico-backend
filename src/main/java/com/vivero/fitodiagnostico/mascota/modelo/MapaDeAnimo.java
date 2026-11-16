package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;

import java.util.Comparator;
import java.util.Optional;

/**
 * Del último diagnóstico y la vitalidad, al ánimo (D136). Un mapa de presentación: el dominio dice "humedad BAJO a
 * 0,42 del ancho del rango"; la mascota lo traduce a SEDIENTA. Si fallan varios parámetros gana el que más se
 * desvía (la desviación relativa del dominio); a igual desviación, el orden de {@link Magnitud}.
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
            // Cuando el diagnóstico traiga `periodo`, luz BAJO de NOCHE será DORMIDA y no A_OSCURAS:
            // dormir no es un problema y la cara no debe pedir ayuda.
            case LUZ -> bajo ? Animo.A_OSCURAS : Animo.DESLUMBRADA;
        };
    }

    private static Optional<Causa> peor(Diagnostico diagnostico) {
        return diagnostico.parametros().stream()
                .filter(ResultadoParametro::fueraDeRango)
                .max(Comparator.comparingDouble(ResultadoParametro::desviacionRelativa)
                        .thenComparing(r -> -r.magnitud().ordinal()))
                .map(r -> new Causa(r.magnitud(), r.clasificacion(), r.desviacionRelativa()));
    }
}
