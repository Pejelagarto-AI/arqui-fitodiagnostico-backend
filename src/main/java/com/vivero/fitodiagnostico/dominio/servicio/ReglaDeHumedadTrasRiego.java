package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Una planta recién regada no está ahogada (fase 15, D200). Justo después de un riego la tierra
 * queda empapada y la humedad del sustrato pasa del máximo de casi cualquier especie; eso es lo
 * esperado y no debe poner la planta en riesgo. Lo que sí es un problema es que siga así mucho
 * tiempo (encharcamiento). Es el mismo razonamiento de la noche para la luz
 * ({@link ReglaDeLuzAcumulada}): la lectura del momento no basta, hace falta el historial.
 * Es la única clase del dominio que nombra {@link Magnitud#HUMEDAD} (D202).
 *
 * <p>La regla (D201):
 * <ol>
 *   <li><b>Riego.</b> Hay riego en una lectura (la <b>candidata</b>) si ella y la siguiente (la
 *       <b>confirmación</b>) superan la mediana de la hora anterior en al menos
 *       {@link #SUBIDA_DE_RIEGO} puntos y la lectura justo antes de la candidata no lo hace. Es el
 *       criterio con que la mascota emite REGADA (D134), copiado a propósito: el diagnóstico no
 *       puede depender del contexto de la mascota (ArchUnit), y los dos tienen que ver el mismo
 *       riego. Un pico de una sola lectura no cuenta; una subida lenta tampoco.</li>
 *   <li><b>Gracia.</b> Si hubo riego en las últimas {@link #GRACIA}, la humedad ALTO no entra al
 *       estado global y su recomendación dice que la planta quedó hidratada y que no hay que volver
 *       a regar. El parámetro sigue diciendo ALTO: el dato no se maquilla.</li>
 *   <li><b>Encharcamiento.</b> Pasada la gracia, la humedad ALTO cuenta como siempre ("suspender el
 *       riego y revisar el drenaje"). Regar una tierra que ya estaba húmeda casi no la sube: no
 *       alcanza el umbral, no hay riego y el exceso cuenta. Es lo correcto: es regar de más.</li>
 *   <li><b>Sin historial.</b> Sin lecturas anteriores no se puede ver una subida: todo sigue como
 *       siempre.</li>
 * </ol>
 */
public final class ReglaDeHumedadTrasRiego {

    /** Puntos de humedad sobre la mediana de la hora anterior para llamar riego. El mismo de la mascota (D134). */
    public static final double SUBIDA_DE_RIEGO = 15.0;

    /** De dónde sale la mediana de referencia de cada candidata. */
    public static final Duration VENTANA_DE_REFERENCIA = Duration.ofHours(1);

    /** Con menos lecturas que esto en la referencia no hay contra qué comparar. El mismo de la mascota. */
    public static final int MUESTRAS_MINIMAS_DE_REFERENCIA = 3;

    /**
     * Cuánto dura la tolerancia después de un riego. Una matera con drenaje suelta el agua que
     * sobra en minutos, pero el sustrato queda húmedo, por encima del rango "entre riegos" de las
     * especies, durante horas y hasta un día. Un día completo cubre ese secado sin tapar un
     * encharcamiento de verdad, que dura días. Ver D201.
     */
    public static final Duration GRACIA = Duration.ofHours(24);

    /** El historial que hace falta: la gracia más la referencia del riego más antiguo que todavía cuenta. */
    public static final Duration HISTORIAL = GRACIA.plus(VENTANA_DE_REFERENCIA);

    /** La magnitud a la que aplica esta regla. */
    public Magnitud magnitud() {
        return Magnitud.HUMEDAD;
    }

    /**
     * El riego más reciente de la gracia, contando esta lectura como la última.
     *
     * @param anteriores   humedad del historial de la planta (puede traer de más; aquí se recorta)
     * @param ahora        cuándo se tomó esta lectura
     * @param humedadAhora la humedad de esta lectura
     * @return cuándo se tomó la lectura candidata del último riego, o vacío si no hubo en la gracia
     */
    public Optional<Instant> ultimoRiego(SerieTemporal anteriores, Instant ahora, double humedadAhora) {
        List<Muestra> muestras = new ArrayList<>(anteriores.entre(ahora.minus(HISTORIAL), ahora));
        muestras.add(new Muestra(ahora, humedadAhora));
        Instant desdeGracia = ahora.minus(GRACIA);

        // La referencia de la candidata i son las lecturas de la hora anterior a ella (índices
        // inicio..i-1). Un mínimo deslizante descarta barato a casi todas las candidatas: si no
        // superan el mínimo más la subida, menos superan la mediana más la subida.
        Deque<Integer> minimos = new ArrayDeque<>();
        int inicio = 0;
        Instant ultimo = null;
        for (int i = 1; i + 1 < muestras.size(); i++) {
            int entra = i - 1;
            while (!minimos.isEmpty() && valor(muestras, minimos.peekLast()) >= valor(muestras, entra)) {
                minimos.pollLast();
            }
            minimos.addLast(entra);
            Instant candidataEn = muestras.get(i).tomadaEn();
            while (muestras.get(inicio).tomadaEn().isBefore(candidataEn.minus(VENTANA_DE_REFERENCIA))) {
                inicio++;
            }
            while (!minimos.isEmpty() && minimos.peekFirst() < inicio) {
                minimos.pollFirst();
            }
            if (candidataEn.isBefore(desdeGracia) || i - inicio < MUESTRAS_MINIMAS_DE_REFERENCIA
                    || minimos.isEmpty()) {
                continue;
            }
            double candidata = valor(muestras, i);
            double confirmacion = valor(muestras, i + 1);
            double piso = valor(muestras, minimos.peekFirst()) + SUBIDA_DE_RIEGO;
            if (candidata < piso || confirmacion < piso) {
                continue;
            }
            double umbral = mediana(muestras, inicio, i) + SUBIDA_DE_RIEGO;
            if (candidata >= umbral && confirmacion >= umbral && valor(muestras, i - 1) < umbral) {
                ultimo = candidataEn;
            }
        }
        return Optional.ofNullable(ultimo);
    }

    /** Si hubo riego en la gracia, la humedad ALTO no cuenta en contra: se quita de lo que agrega el estado. */
    public List<ResultadoParametro> queCuentanParaElEstado(List<ResultadoParametro> parametros, Instant regadaEn) {
        if (regadaEn == null) {
            return parametros;
        }
        return parametros.stream().filter(p -> !tolerada(p)).toList();
    }

    /** La humedad ALTO que la gracia tolera. BAJO nunca se tolera: recién regada no puede estar seca. */
    public boolean tolerada(ResultadoParametro parametro) {
        return parametro.magnitud() == magnitud() && parametro.clasificacion() == Clasificacion.ALTO;
    }

    /**
     * La recomendación de la humedad tolerada, con la forma de las de {@link RedactorDeRecomendaciones}
     * ("descripción valor comparación rango: acción.") para que quien las presente las trate igual.
     */
    public String recomendacionTrasRiego(ResultadoParametro humedad, Instant regadaEn, Instant ahora) {
        Rango rango = humedad.rangoOptimo();
        String unidad = magnitud().unidad();
        return capitalizar(magnitud().descripcion()) + " " + numero(humedad.valorEvaluado()) + " " + unidad
                + " por encima del rango óptimo " + numero(rango.minimo()) + "–" + numero(rango.maximo()) + " "
                + unidad + ": la regaste " + haceCuanto(regadaEn, ahora)
                + " y quedó bien hidratada; no volver a regar hasta que baje de " + numero(rango.maximo())
                + " " + unidad + ".";
    }

    private static String haceCuanto(Instant regadaEn, Instant ahora) {
        long minutos = Math.max(0, Duration.between(regadaEn, ahora).toMinutes());
        if (minutos < 1) {
            return "hace un momento";
        }
        if (minutos < 60) {
            return "hace " + minutos + " min";
        }
        return "hace " + (minutos / 60) + " h";
    }

    private static double valor(List<Muestra> muestras, int indice) {
        return muestras.get(indice).valor();
    }

    /** Mediana de los valores con índice en [desde, hasta). */
    private static double mediana(List<Muestra> muestras, int desde, int hasta) {
        double[] valores = new double[hasta - desde];
        for (int k = desde; k < hasta; k++) {
            valores[k - desde] = muestras.get(k).valor();
        }
        Arrays.sort(valores);
        int mitad = valores.length / 2;
        return valores.length % 2 == 1 ? valores[mitad] : (valores[mitad - 1] + valores[mitad]) / 2.0;
    }

    private static String capitalizar(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Sin ".0" cuando el valor es un entero, como en las demás recomendaciones. */
    private static String numero(double valor) {
        if (valor == Math.rint(valor) && !Double.isInfinite(valor)) {
            return String.valueOf((long) valor);
        }
        return String.valueOf(valor);
    }
}
