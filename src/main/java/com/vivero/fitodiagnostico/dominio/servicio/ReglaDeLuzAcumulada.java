package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.BaseDeEvaluacion;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * La luz se juzga por lo acumulado en el día, no por la lectura del momento (fase 8,
 * decisión de Santiago del 12-nov). Una planta a las 10 pm sin luz no está en riesgo:
 * duerme. Es la única clase del dominio que nombra {@link Magnitud#LUZ}, porque el
 * fotoperiodo es una propiedad de la luz y de ninguna otra magnitud (D124).
 *
 * <p>La regla (D123):
 * <ol>
 *   <li><b>Ventana.</b> Las lecturas de las últimas {@link #VENTANA} antes de esta, más esta.</li>
 *   <li><b>Tramos.</b> Cada lectura describe el tramo desde la lectura anterior, con un tope de
 *       {@link #HUECO_MAXIMO}: si la matera estuvo apagada no se inventa qué luz hubo.
 *       La suma de tramos son las {@code horasObservadas}.</li>
 *   <li><b>Horas de luz.</b> Un tramo "tiene luz" si su lectura es de al menos
 *       {@link #UMBRAL_HAY_LUZ_LUX}.</li>
 *   <li><b>Lux efectivo.</b> La luz de las horas con luz (lux·h) dividida entre lo que sea
 *       mayor: las horas de luz que hubo o las exigidas ({@link #HORAS_MINIMAS_DE_LUZ} por día,
 *       proporcionales a lo observado). Con suficientes horas es el promedio de lux en las horas
 *       con luz; con pocas, ese promedio se diluye. Se compara con el rango de la especie:
 *       BAJO si no llega, ALTO si lo pasa, OPTIMO si cae dentro.</li>
 *   <li><b>Noche.</b> Si esta lectura y las anteriores llevan al menos {@link #OSCURIDAD_PARA_NOCHE}
 *       por debajo del umbral de luz, es de noche: la luz no entra al estado global ni a las
 *       recomendaciones.</li>
 *   <li><b>Sin historial.</b> Si no hay lecturas anteriores en la ventana, no hay exposición que
 *       medir: la luz se clasifica con la lectura del momento, como siempre.</li>
 * </ol>
 */
public final class ReglaDeLuzAcumulada {

    /** Cuánto historial cuenta: un ciclo completo de día y noche. */
    public static final Duration VENTANA = Duration.ofHours(24);

    /**
     * Desde cuántos lux un tramo cuenta como "con luz". Es del ambiente, no de la especie:
     * día y noche son los mismos para todas las plantas del cuarto, y la OLED debe dormirlas
     * igual. 100 lux es del orden del punto de compensación de las plantas de sombra (por
     * debajo no ganan nada) y queda por debajo del mínimo de luz de todas las especies del
     * catálogo (el más bajo es el del helecho, 150 lux). Una lámpara de cuarto encendida
     * (unos 150 a 300 lux) cuenta como luz. Ver D125.
     */
    public static final double UMBRAL_HAY_LUZ_LUX = 100.0;

    /**
     * Horas de luz que se le exigen a la planta en un día completo observado. Seis horas es
     * lo que piden las fichas de cultivo para las especies de sol del catálogo y queda por
     * debajo de cualquier fotoperiodo natural en Bogotá (unas 12 h todo el año). Constante y
     * no por especie: hacerla por especie es una columna nueva del catálogo (ver límites).
     */
    public static final double HORAS_MINIMAS_DE_LUZ = 6.0;

    /** Cuánta oscuridad sostenida hace falta para decir que es de noche. Ver D126. */
    public static final Duration OSCURIDAD_PARA_NOCHE = Duration.ofHours(2);

    /** El tramo más largo que puede describir una sola lectura. Ver D123. */
    public static final Duration HUECO_MAXIMO = Duration.ofMinutes(30);

    private static final double SEGUNDOS_POR_HORA = 3600.0;
    private static final double HORAS_DE_LA_VENTANA = VENTANA.toSeconds() / SEGUNDOS_POR_HORA;

    /** La magnitud a la que aplica esta regla. */
    public Magnitud magnitud() {
        return Magnitud.LUZ;
    }

    /**
     * Mide la exposición de las últimas 24 h, terminando en esta lectura.
     *
     * @param anteriores lux del historial de la planta (puede traer más de la ventana; aquí se recorta)
     * @param ahora      cuándo se tomó esta lectura
     * @param luxAhora   el lux de esta lectura
     * @return vacío si no hay lecturas anteriores en la ventana: no hay historial y la luz
     *         se evalúa con la lectura del momento
     */
    public Optional<ExposicionLuminica> medir(SerieTemporal anteriores, Instant ahora, double luxAhora) {
        List<Muestra> muestras = new ArrayList<>(anteriores.entre(ahora.minus(VENTANA), ahora));
        if (muestras.isEmpty()) {
            return Optional.empty();
        }
        muestras.add(new Muestra(ahora, luxAhora));

        double segundosObservados = 0;
        double segundosDeLuz = 0;
        double luxPorSegundoDeLuz = 0;
        for (int i = 1; i < muestras.size(); i++) {
            Muestra muestra = muestras.get(i);
            Duration tramo = Duration.between(muestras.get(i - 1).tomadaEn(), muestra.tomadaEn());
            double segundos = (tramo.compareTo(HUECO_MAXIMO) > 0 ? HUECO_MAXIMO : tramo).toMillis() / 1000.0;
            segundosObservados += segundos;
            if (hayLuz(muestra.valor())) {
                segundosDeLuz += segundos;
                luxPorSegundoDeLuz += muestra.valor() * segundos;
            }
        }
        if (segundosObservados <= 0) {
            return Optional.empty();   // todas las lecturas en el mismo instante: no cubren tiempo
        }

        double horasObservadas = segundosObservados / SEGUNDOS_POR_HORA;
        double horasDeLuz = segundosDeLuz / SEGUNDOS_POR_HORA;
        double luxHora = luxPorSegundoDeLuz / SEGUNDOS_POR_HORA;
        double luxPromedioEnLuz = horasDeLuz > 0 ? luxHora / horasDeLuz : 0.0;
        double horasExigidas = HORAS_MINIMAS_DE_LUZ * horasObservadas / HORAS_DE_LA_VENTANA;
        double luxEfectivo = luxHora / Math.max(horasDeLuz, horasExigidas);

        return Optional.of(new ExposicionLuminica(horasObservadas, horasDeLuz, luxPromedioEnLuz,
                luxEfectivo, horasEnOscuridad(muestras, ahora)));
    }

    /** NOCHE si la oscuridad lleva al menos {@link #OSCURIDAD_PARA_NOCHE}; el borde exacto ya es noche. */
    public Periodo periodo(ExposicionLuminica exposicion) {
        double horasParaNoche = OSCURIDAD_PARA_NOCHE.toSeconds() / SEGUNDOS_POR_HORA;
        return exposicion.horasEnOscuridad() >= horasParaNoche ? Periodo.NOCHE : Periodo.DIA;
    }

    /** El parámetro de luz clasificado por el lux efectivo; {@code valor} sigue siendo la lectura del momento. */
    public ResultadoParametro clasificar(Rango rangoOptimo, double luxAhora, ExposicionLuminica exposicion) {
        double evaluado = exposicion.luxEfectivo();
        return new ResultadoParametro(magnitud(), luxAhora, rangoOptimo, rangoOptimo.clasificar(evaluado),
                evaluado, BaseDeEvaluacion.ACUMULADA);
    }

    /** De noche la luz no cuenta en contra: se quita de lo que agrega el estado y de las recomendaciones. */
    public List<ResultadoParametro> queCuentanParaElEstado(List<ResultadoParametro> parametros, Periodo periodo) {
        if (periodo == Periodo.DIA) {
            return parametros;
        }
        return parametros.stream().filter(p -> p.magnitud() != magnitud()).toList();
    }

    private static boolean hayLuz(double lux) {
        return lux >= UMBRAL_HAY_LUZ_LUX;
    }

    /**
     * Desde la lectura oscura más antigua de la racha que termina en esta, hasta ahora. Se
     * cuenta desde esa lectura y no desde la última con luz: entre las dos no se sabe cuándo
     * se fue la luz, y es mejor tardar un poco en dormir la planta que dormirla de más.
     */
    private static double horasEnOscuridad(List<Muestra> muestras, Instant ahora) {
        int inicio = muestras.size();
        while (inicio > 0 && !hayLuz(muestras.get(inicio - 1).valor())) {
            inicio--;
        }
        if (inicio == muestras.size()) {
            return 0.0;   // ahora hay luz
        }
        return Duration.between(muestras.get(inicio).tomadaEn(), ahora).toMillis() / 1000.0 / SEGUNDOS_POR_HORA;
    }
}
