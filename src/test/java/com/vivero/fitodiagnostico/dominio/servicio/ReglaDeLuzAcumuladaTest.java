package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.BaseDeEvaluacion;
import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * La regla de luz acumulada (fase 8, D123 a D126), sin servidor ni base: la serie de
 * lux se arma a mano. Un día de lecturas cada 15 min: la lectura k (1 a 96) se tomó
 * k cuartos de hora antes de "ahora". Cada lectura describe el cuarto de hora que
 * termina en ella, así que "k <= 11 con luz, más la actual" son 12 tramos = 3 h.
 */
class ReglaDeLuzAcumuladaTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T17:00:00Z");
    private static final Duration CUARTO = Duration.ofMinutes(15);
    private static final Rango SUCULENTA = new Rango(800, 2500);

    private final ReglaDeLuzAcumulada regla = new ReglaDeLuzAcumulada();

    /** Un día de lecturas cada 15 min antes de AHORA; {@code lux.applyAsDouble(k)} es la lectura k. */
    private static SerieTemporal unDia(IntToDoubleFunction lux) {
        List<Muestra> muestras = new ArrayList<>();
        for (int k = 1; k <= 96; k++) {
            muestras.add(new Muestra(AHORA.minus(CUARTO.multipliedBy(k)), lux.applyAsDouble(k)));
        }
        return SerieTemporal.de(muestras);
    }

    private ExposicionLuminica medir(SerieTemporal serie, double luxAhora) {
        return regla.medir(serie, AHORA, luxAhora).orElseThrow();
    }

    @Test
    void sinHistorialNoHayExposicionYLaLuzSeEvaluaComoSiempre() {
        assertThat(regla.medir(SerieTemporal.vacia(), AHORA, 0)).isEmpty();
    }

    @Test
    void lecturasFueraDeLaVentanaOPosterioresNoCuentanComoHistorial() {
        // Una de hace 25 h y una "del futuro" (un reenvío con marca posterior a esta lectura).
        SerieTemporal serie = SerieTemporal.de(List.of(
                new Muestra(AHORA.minus(Duration.ofHours(25)), 1500),
                new Muestra(AHORA.plus(Duration.ofHours(1)), 1500)));

        assertThat(regla.medir(serie, AHORA, 1500)).isEmpty();
    }

    @Test
    void unDiaConBuenaExposicionEsOptimoYGuardaLaLecturaDelMomento() {
        // 10 h a 1500 lux y el resto a 10 lux. La lectura del momento queda en valor; se evalúa el acumulado.
        SerieTemporal serie = unDia(k -> k <= 39 ? 1500 : 10);

        ExposicionLuminica exposicion = medir(serie, 1500);
        ResultadoParametro luz = regla.clasificar(SUCULENTA, 300, exposicion);

        assertThat(exposicion.horasObservadas()).isCloseTo(24.0, within(1e-9));
        assertThat(exposicion.horasDeLuz()).isCloseTo(10.0, within(1e-9));
        assertThat(exposicion.luxEfectivo()).isCloseTo(1500.0, within(1e-9));
        assertThat(luz.clasificacion()).isEqualTo(Clasificacion.OPTIMO);
        assertThat(luz.valor()).isEqualTo(300);
        assertThat(luz.valorEvaluado()).isCloseTo(1500.0, within(1e-9));
        assertThat(luz.base()).isEqualTo(BaseDeEvaluacion.ACUMULADA);
        assertThat(regla.periodo(exposicion)).isEqualTo(Periodo.DIA);
    }

    @Test
    void pocasHorasDeLuzDiluyenElPromedioYDanBajo() {
        // 3 h a 1500 lux: el promedio en luz está en rango, pero 1500 x 3 h / 6 h = 750 < 800.
        ExposicionLuminica exposicion = medir(unDia(k -> k <= 11 ? 1500 : 0), 1500);

        assertThat(exposicion.horasDeLuz()).isCloseTo(3.0, within(1e-9));
        assertThat(exposicion.luxPromedioEnLuz()).isCloseTo(1500.0, within(1e-9));
        assertThat(exposicion.luxEfectivo()).isCloseTo(750.0, within(1e-9));
        assertThat(regla.clasificar(SUCULENTA, 1500, exposicion).clasificacion()).isEqualTo(Clasificacion.BAJO);
    }

    @Test
    void unPromedioEnLuzPorEncimaDelRangoEsAlto() {
        ExposicionLuminica exposicion = medir(unDia(k -> k <= 39 ? 4000 : 0), 4000);

        assertThat(regla.clasificar(SUCULENTA, 4000, exposicion).clasificacion()).isEqualTo(Clasificacion.ALTO);
    }

    @Test
    void seisHorasJustasAlMinimoDelRangoSonOptimoYUnCuartoMenosEsBajo() {
        ExposicionLuminica seis = medir(unDia(k -> k <= 23 ? 800 : 0), 800);
        ExposicionLuminica casiSeis = medir(unDia(k -> k <= 22 ? 800 : 0), 800);

        assertThat(seis.horasDeLuz()).isCloseTo(6.0, within(1e-9));
        assertThat(seis.luxEfectivo()).isCloseTo(800.0, within(1e-9));
        assertThat(regla.clasificar(SUCULENTA, 800, seis).clasificacion()).isEqualTo(Clasificacion.OPTIMO);
        assertThat(casiSeis.luxEfectivo()).isCloseTo(800 * 5.75 / 6, within(1e-9));
        assertThat(regla.clasificar(SUCULENTA, 800, casiSeis).clasificacion()).isEqualTo(Clasificacion.BAJO);
    }

    @Test
    void elUmbralDeHayLuzIncluyeSuBorde() {
        double umbral = ReglaDeLuzAcumulada.UMBRAL_HAY_LUZ_LUX;

        assertThat(medir(unDia(k -> umbral), umbral).horasDeLuz()).isCloseTo(24.0, within(1e-9));
        assertThat(medir(unDia(k -> umbral - 0.1), umbral - 0.1).horasDeLuz()).isZero();
    }

    @Test
    void tresHorasDeOscuridadDespuesDeUnBuenDiaEsNoche() {
        // Luz de k = 13 a 52 (10 h), oscuridad desde k = 12 (hace 3 h) hasta ahora.
        ExposicionLuminica exposicion = medir(unDia(k -> k >= 13 && k <= 52 ? 1500 : 0), 0);

        assertThat(exposicion.horasEnOscuridad()).isCloseTo(3.0, within(1e-9));
        assertThat(regla.periodo(exposicion)).isEqualTo(Periodo.NOCHE);
        assertThat(regla.clasificar(SUCULENTA, 0, exposicion).clasificacion()).isEqualTo(Clasificacion.OPTIMO);
    }

    @Test
    void laNocheEmpiezaJustoALasDosHorasDeOscuridad() {
        ExposicionLuminica dosHoras = medir(unDia(k -> k <= 8 ? 0 : 1500), 0);
        ExposicionLuminica horaYTresCuartos = medir(unDia(k -> k <= 7 ? 0 : 1500), 0);

        assertThat(dosHoras.horasEnOscuridad()).isCloseTo(2.0, within(1e-9));
        assertThat(regla.periodo(dosHoras)).isEqualTo(Periodo.NOCHE);
        assertThat(horaYTresCuartos.horasEnOscuridad()).isCloseTo(1.75, within(1e-9));
        assertThat(regla.periodo(horaYTresCuartos)).isEqualTo(Periodo.DIA);
    }

    @Test
    void siAhoraHayLuzNoEsNocheAunqueAntesEstuvieraOscuro() {
        ExposicionLuminica exposicion = medir(unDia(k -> 0), 1500);

        assertThat(exposicion.horasEnOscuridad()).isZero();
        assertThat(regla.periodo(exposicion)).isEqualTo(Periodo.DIA);
    }

    @Test
    void unHuecoLargoSoloCuentaHastaElTope() {
        // La matera estuvo apagada 10 h: la lectura de ahora describe 30 min, no 10 h.
        SerieTemporal serie = SerieTemporal.de(List.of(new Muestra(AHORA.minus(Duration.ofHours(10)), 1500)));

        ExposicionLuminica exposicion = medir(serie, 1500);

        assertThat(exposicion.horasObservadas()).isCloseTo(0.5, within(1e-9));
        assertThat(exposicion.horasDeLuz()).isCloseTo(0.5, within(1e-9));
        // Lo exigido es proporcional a lo observado (6 h x 0,5 / 24 = 0,125 h): media hora de buena luz alcanza.
        assertThat(exposicion.luxEfectivo()).isCloseTo(1500.0, within(1e-9));
    }

    @Test
    void conPocoHistorialLaLecturaDelMomentoPesaTodo() {
        // Dos lecturas: la anterior no describe ningún tramo, la de ahora describe el que las separa.
        SerieTemporal serie = SerieTemporal.de(List.of(new Muestra(AHORA.minusSeconds(5), 1500)));

        ExposicionLuminica exposicion = medir(serie, 50);

        assertThat(exposicion.horasDeLuz()).isZero();
        assertThat(exposicion.luxEfectivo()).isZero();
        assertThat(regla.clasificar(SUCULENTA, 50, exposicion).clasificacion()).isEqualTo(Clasificacion.BAJO);
    }

    @Test
    void deNocheLaLuzNoCuentaParaElEstadoYDeDiaSi() {
        ResultadoParametro luz = new ResultadoParametro(regla.magnitud(), 0, SUCULENTA, Clasificacion.BAJO);
        ResultadoParametro humedad = new ResultadoParametro(
                com.vivero.fitodiagnostico.dominio.modelo.Magnitud.HUMEDAD, 20, new Rango(10, 30), Clasificacion.OPTIMO);

        assertThat(regla.queCuentanParaElEstado(List.of(humedad, luz), Periodo.NOCHE)).containsExactly(humedad);
        assertThat(regla.queCuentanParaElEstado(List.of(humedad, luz), Periodo.DIA)).containsExactly(humedad, luz);
    }
}
