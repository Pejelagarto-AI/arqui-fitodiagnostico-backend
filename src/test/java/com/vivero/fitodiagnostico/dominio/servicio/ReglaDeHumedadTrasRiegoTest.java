package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla de la planta recién regada (fase 15, D201) con series simuladas. Los números salen de
 * la calibración real de la sonda (2026-11-26): tierra que se ve seca ~20 %, recién regada ~100 %
 * y escurrida 70 a 90 %; el ruido de la sonda en tierra quieta es de unos ±3 puntos.
 */
class ReglaDeHumedadTrasRiegoTest {

    private static final Instant AHORA = Instant.parse("2026-11-26T17:00:00Z");
    private static final Duration MINUTO = Duration.ofMinutes(1);

    private final ReglaDeHumedadTrasRiego regla = new ReglaDeHumedadTrasRiego();

    /** Una lectura por minuto: {@code valores[0]} es la más antigua y la última es la de hace un minuto. */
    private static SerieTemporal porMinuto(double... valores) {
        List<Muestra> muestras = new ArrayList<>();
        for (int k = 0; k < valores.length; k++) {
            muestras.add(new Muestra(AHORA.minus(MINUTO.multipliedBy(valores.length - k)), valores[k]));
        }
        return SerieTemporal.de(muestras);
    }

    /** {@code n} lecturas de tierra seca con un ruido de ±3 puntos que se repite. */
    private static double[] seca(int n) {
        double[] valores = new double[n];
        for (int k = 0; k < n; k++) {
            valores[k] = 20 + (k % 3 - 1) * 3;
        }
        return valores;
    }

    private static double[] unir(double[]... partes) {
        int total = 0;
        for (double[] parte : partes) {
            total += parte.length;
        }
        double[] todo = new double[total];
        int i = 0;
        for (double[] parte : partes) {
            System.arraycopy(parte, 0, todo, i, parte.length);
            i += parte.length;
        }
        return todo;
    }

    private static double[] repetir(double valor, int n) {
        double[] valores = new double[n];
        java.util.Arrays.fill(valores, valor);
        return valores;
    }

    @Test
    void sinHistorialNoHayRiegoQueVer() {
        assertThat(regla.ultimoRiego(SerieTemporal.vacia(), AHORA, 100)).isEmpty();
    }

    @Test
    void unRiegoDeVerdadSeDetectaEnLaLecturaDondeEmpiezaLaSubida() {
        // 30 min de tierra seca, el riego (sube en dos lecturas) y 5 min de tierra empapada.
        SerieTemporal serie = porMinuto(unir(seca(30), new double[] {60, 95}, repetir(98, 5)));

        Optional<Instant> riego = regla.ultimoRiego(serie, AHORA, 97);

        // La candidata es la primera que supera mediana (20) + 15: la de 60, hace 7 minutos.
        assertThat(riego).contains(AHORA.minus(MINUTO.multipliedBy(7)));
    }

    @Test
    void laLecturaDelMomentoConfirmaUnRiegoQueEmpezoEnLaAnterior() {
        SerieTemporal serie = porMinuto(unir(seca(30), new double[] {90}));

        assertThat(regla.ultimoRiego(serie, AHORA, 96)).contains(AHORA.minus(MINUTO));
    }

    @Test
    void unaSolaLecturaAltaNoEsRiegoPorqueNadieLaConfirma() {
        // La sonda se movió: un pico de una lectura y vuelve a lo seco.
        SerieTemporal serie = porMinuto(unir(seca(30), new double[] {90}, seca(5)));

        assertThat(regla.ultimoRiego(serie, AHORA, 21)).isEmpty();
    }

    @Test
    void unaSubidaLentaNoEsRiego() {
        // De 20 a 50 % en dos horas, un punto cada 4 minutos: nunca 15 sobre la mediana de su hora.
        double[] lenta = new double[120];
        for (int k = 0; k < lenta.length; k++) {
            lenta[k] = 20 + k / 4.0;
        }
        assertThat(regla.ultimoRiego(porMinuto(lenta), AHORA, 50)).isEmpty();
    }

    @Test
    void regarUnaTierraQueYaEstabaHumedaNoCuentaComoRiego() {
        // Estaba en 85 % y la riegan otra vez: sube a 98, apenas 13 puntos.
        SerieTemporal serie = porMinuto(unir(repetir(85, 30), repetir(98, 5)));

        assertThat(regla.ultimoRiego(serie, AHORA, 98)).isEmpty();
    }

    @Test
    void conMenosDeTresLecturasDeReferenciaNoHayContraQueComparar() {
        SerieTemporal serie = porMinuto(20, 20, 95, 97);

        assertThat(regla.ultimoRiego(serie, AHORA, 97)).isEmpty();
    }

    @Test
    void unRiegoDeHaceMasDeUnDiaYaNoCuenta() {
        Instant riego = AHORA.minus(Duration.ofHours(25));
        List<Muestra> muestras = new ArrayList<>();
        for (int k = 30; k >= 1; k--) {
            muestras.add(new Muestra(riego.minus(MINUTO.multipliedBy(k)), 20));
        }
        muestras.add(new Muestra(riego, 95));
        muestras.add(new Muestra(riego.plus(MINUTO), 96));
        // Sigue empapada desde entonces, una lectura cada 15 minutos.
        for (Instant t = riego.plus(Duration.ofMinutes(15)); t.isBefore(AHORA); t = t.plus(Duration.ofMinutes(15))) {
            muestras.add(new Muestra(t, 95));
        }

        assertThat(regla.ultimoRiego(SerieTemporal.de(muestras), AHORA, 95)).isEmpty();
    }

    @Test
    void justoAntesDeCumplirseElDiaTodaviaCuenta() {
        Instant riego = AHORA.minus(Duration.ofHours(23));
        List<Muestra> muestras = new ArrayList<>();
        for (int k = 30; k >= 1; k--) {
            muestras.add(new Muestra(riego.minus(MINUTO.multipliedBy(k)), 20));
        }
        muestras.add(new Muestra(riego, 95));
        muestras.add(new Muestra(riego.plus(MINUTO), 96));

        assertThat(regla.ultimoRiego(SerieTemporal.de(muestras), AHORA, 80)).contains(riego);
    }

    @Test
    void conDosRiegosDevuelveElMasReciente() {
        // Regada, se seca en la siguiente hora y media, y la vuelven a regar.
        double[] secado = new double[90];
        for (int k = 0; k < secado.length; k++) {
            secado[k] = Math.max(20, 95 - k);
        }
        SerieTemporal serie = porMinuto(unir(seca(30), new double[] {95}, secado, seca(60), new double[] {90}));

        assertThat(regla.ultimoRiego(serie, AHORA, 96)).contains(AHORA.minus(MINUTO));
    }

    @Test
    void conRiegoSoloSeQuitaLaHumedadAlta() {
        Rango rango = new Rango(10, 30);
        ResultadoParametro humedadAlta = new ResultadoParametro(Magnitud.HUMEDAD, 95, rango, Clasificacion.ALTO);
        ResultadoParametro luzBaja = new ResultadoParametro(Magnitud.LUZ, 200, new Rango(800, 2500),
                Clasificacion.BAJO);
        List<ResultadoParametro> parametros = List.of(humedadAlta, luzBaja);

        assertThat(regla.queCuentanParaElEstado(parametros, AHORA)).containsExactly(luzBaja);
        assertThat(regla.queCuentanParaElEstado(parametros, null)).containsExactly(humedadAlta, luzBaja);
    }

    @Test
    void laHumedadBajaNuncaSeTolera() {
        ResultadoParametro humedadBaja = new ResultadoParametro(Magnitud.HUMEDAD, 5, new Rango(10, 30),
                Clasificacion.BAJO);

        assertThat(regla.tolerada(humedadBaja)).isFalse();
        assertThat(regla.queCuentanParaElEstado(List.of(humedadBaja), AHORA)).containsExactly(humedadBaja);
    }

    @Test
    void laRecomendacionTieneLaFormaDeLasDemasYDiceCuandoSeRego() {
        ResultadoParametro humedad = new ResultadoParametro(Magnitud.HUMEDAD, 95, new Rango(10, 30),
                Clasificacion.ALTO);

        assertThat(regla.recomendacionTrasRiego(humedad, AHORA.minus(Duration.ofMinutes(3)), AHORA))
                .isEqualTo("Humedad del sustrato 95 % por encima del rango óptimo 10–30 %: la regaste hace 3 min"
                        + " y quedó bien hidratada; no volver a regar hasta que baje de 30 %.");
        assertThat(regla.recomendacionTrasRiego(humedad, AHORA.minusSeconds(20), AHORA)).contains("hace un momento");
        assertThat(regla.recomendacionTrasRiego(humedad, AHORA.minus(Duration.ofHours(5)), AHORA))
                .contains("hace 5 h");
    }
}
