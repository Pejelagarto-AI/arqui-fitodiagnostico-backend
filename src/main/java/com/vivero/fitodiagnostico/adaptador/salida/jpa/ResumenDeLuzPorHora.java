package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeLuzAcumulada;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lo que la regla de luz acumulada (fase 8) necesita de las lecturas de una hora, sin las lecturas (D175).
 *
 * <p>La regla recorre una serie de muestras: cada una describe el tramo desde la anterior (con tope de
 * {@link ReglaDeLuzAcumulada#HUECO_MAXIMO}), un tramo "tiene luz" si su lectura llega a
 * {@link ReglaDeLuzAcumulada#UMBRAL_HAY_LUZ_LUX}, y la noche se cuenta desde la lectura oscura más antigua de la racha
 * que termina en la última. Un resumen guarda los cuatro números que bastan para reproducir eso:
 * <ul>
 *   <li>{@code segundosObservados}: la suma de los tramos de las lecturas de la hora;</li>
 *   <li>{@code segundosConLuz} y {@code luxSegundos}: la parte con luz y su lux por segundo;</li>
 *   <li>{@code segundosEnOscuridadFinal}: los tramos oscuros después de la última lectura con luz de la hora (si no
 *       hubo luz, toda la hora), y {@code primerTramoOscuro}, el del primero de ellos: juntos dicen desde cuándo dura la
 *       oscuridad (la noche se cuenta desde la lectura oscura más antigua de la racha).</li>
 * </ul>
 *
 * <p>{@link #comoMuestras} lo convierte en pocas muestras sintéticas que, puestas en la serie, hacen que la regla del
 * dominio llegue a los mismos números que con las lecturas crudas (D175): una oscuridad inicial, la luz con su lux
 * medio, y la oscuridad final. El orden dentro de la hora se pierde salvo en lo que importa a la noche (el final).
 * Las diferencias con las crudas son de segundos por hora: ver las aproximaciones en {@code docs/fase12}.
 *
 * <p>Pura: sin Spring ni base. La entidad la guarda y la regla del dominio la consume.
 */
record ResumenDeLuzPorHora(UUID plantaId, Instant hora, int lecturas, double segundosObservados,
                           double segundosConLuz, double luxSegundos, double segundosEnOscuridadFinal,
                           double primerTramoOscuro, Instant ultimaLecturaEn) {

    static final Duration UNA_HORA = Duration.ofHours(1);
    private static final double CHUNK_SEGUNDOS = ReglaDeLuzAcumulada.HUECO_MAXIMO.toSeconds();

    /**
     * Calcula el resumen de la hora que empieza en {@code hora}.
     *
     * @param previa la última muestra anterior a la hora (cuyo tramo hasta la primera de la hora se cuenta aquí), o
     *               {@code null}
     * @param deLaHora las muestras con {@code hora <= tomadaEn < hora + 1 h}, en orden
     */
    static ResumenDeLuzPorHora de(UUID plantaId, Instant hora, Muestra previa, List<Muestra> deLaHora) {
        double observados = 0;
        double conLuz = 0;
        double luxSegundos = 0;
        double oscuridadFinal = 0;
        double primerTramoOscuro = 0;
        boolean primeraOscura = true;
        Muestra anterior = previa;
        for (Muestra muestra : deLaHora) {
            double tramo = anterior == null ? 0 : segundos(anterior.tomadaEn(), muestra.tomadaEn());
            observados += tramo;
            if (muestra.valor() >= ReglaDeLuzAcumulada.UMBRAL_HAY_LUZ_LUX) {
                conLuz += tramo;
                luxSegundos += muestra.valor() * tramo;
                oscuridadFinal = 0;
                primeraOscura = true;
            } else {
                if (primeraOscura) {
                    primerTramoOscuro = tramo;
                    primeraOscura = false;
                }
                oscuridadFinal += tramo;
            }
            anterior = muestra;
        }
        return new ResumenDeLuzPorHora(plantaId, hora, deLaHora.size(), observados, conLuz, luxSegundos, oscuridadFinal,
                primerTramoOscuro, deLaHora.isEmpty() ? null : deLaHora.get(deLaHora.size() - 1).tomadaEn());
    }

    private static double segundos(Instant desde, Instant hasta) {
        double s = Duration.between(desde, hasta).toMillis() / 1000.0;
        return Math.min(Math.max(s, 0), CHUNK_SEGUNDOS);
    }

    /**
     * Las muestras sintéticas de varias horas cerradas y consecutivas, "pegadas" y alineadas a la derecha: la última
     * termina donde terminó de verdad la última lectura con datos ({@code ultimaLecturaEn}). Pegarlas hace que cada
     * tramo mida exactamente lo que el resumen dice, aunque hubiera huecos sin lectura dentro de la hora; alinearlas a
     * la derecha en la última lectura real hace que el primer tramo de las crudas siga siendo el de pocos segundos que
     * fue. La primera muestra es un ancla con luz (la regla ignora el tramo de la primera muestra de la serie, como con
     * las crudas).
     *
     * <p>Nada queda antes de {@code desde}: si las horas suman más que el espacio, se recorta lo más antiguo.
     */
    static List<Muestra> comoSerie(List<ResumenDeLuzPorHora> resumenes, Instant desde) {
        List<Muestra> serie = new ArrayList<>();
        Instant fin = null;
        double total = 0;
        List<double[]> tramos = new ArrayList<>();      // {segundos, valor}
        for (ResumenDeLuzPorHora r : resumenes) {
            for (double[] tramo : r.tramos()) {
                tramos.add(tramo);
                total += tramo[0];
            }
            if (r.ultimaLecturaEn() != null) {
                fin = r.ultimaLecturaEn();
            }
        }
        if (tramos.isEmpty() || fin == null) {
            return serie;
        }
        Instant inicio = fin.minusNanos(Math.round(total * 1e9));
        if (inicio.isBefore(desde)) {
            inicio = desde;
        }
        // El ancla no cuenta como oscura: la racha de oscuridad empieza en la primera muestra de verdad (la de su primer tramo).
        serie.add(new Muestra(inicio, ReglaDeLuzAcumulada.UMBRAL_HAY_LUZ_LUX));
        double acumulado = 0;
        for (double[] tramo : tramos) {
            acumulado += tramo[0];
            Instant t = fin.minusNanos(Math.round((total - acumulado) * 1e9));
            if (t.isAfter(inicio)) {
                serie.add(new Muestra(t, tramo[1]));
            }
        }
        return serie;
    }

    /** {segundos, valor} de cada muestra sintética de esta hora: oscuridad inicial, luz, oscuridad final. */
    private List<double[]> tramos() {
        List<double[]> tramos = new ArrayList<>();
        if (lecturas == 0 || segundosObservados <= 0) {
            return tramos;
        }
        double luz = Math.min(segundosConLuz, segundosObservados);
        double oscuridadFinal = Math.min(segundosEnOscuridadFinal, segundosObservados - luz);
        double oscuridadInicial = Math.max(segundosObservados - luz - oscuridadFinal, 0);
        double luxMedio = luz > 0 ? Math.max(luxSegundos / luz, ReglaDeLuzAcumulada.UMBRAL_HAY_LUZ_LUX) : 0;
        partir(tramos, oscuridadInicial, 0.0, 0);
        partir(tramos, luz, luxMedio, 0);
        partir(tramos, oscuridadFinal, 0.0, Math.min(primerTramoOscuro, oscuridadFinal));
        return tramos;
    }

    /** Parte {@code segundos} en tramos de a lo más {@link #CHUNK_SEGUNDOS}; el primero puede ser más corto. */
    private static void partir(List<double[]> salida, double segundos, double valor, double primerTramo) {
        double falta = segundos;
        boolean primero = primerTramo > 0;
        while (falta > 1e-6) {
            double tramo = Math.min(primero ? primerTramo : CHUNK_SEGUNDOS, falta);
            primero = false;
            salida.add(new double[]{tramo, valor});
            falta -= tramo;
        }
    }
}
