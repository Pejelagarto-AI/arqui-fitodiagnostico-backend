package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

/** La recomendación de la luz acumulada (fase 17): dice el lux efectivo y mira la lectura del momento. */
class RecomendacionDeLuzAcumuladaTest {

    private static final Instant MEDIODIA = Instant.parse("2026-11-27T17:00:00Z");   // 12:00 en Bogotá
    private static final Instant DIEZ_PM = Instant.parse("2026-11-28T03:00:00Z");    // 22:00 en Bogotá
    private static final Duration CUARTO = Duration.ofMinutes(15);

    private final Especie suculenta = new Especie("suculenta", Map.of(
            Magnitud.HUMEDAD, new Rango(10, 30),
            Magnitud.LUZ, new Rango(800, 2500),
            Magnitud.TEMPERATURA, new Rango(15, 32)));

    private final EvaluadorDeEstado evaluador = new EvaluadorDeEstado(new ClasificadorDeParametros(),
            new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO), new RedactorDeRecomendaciones());

    private static Medicion medicion(double humedad, double luz) {
        return Medicion.de(new Lectura(Magnitud.HUMEDAD, humedad), new Lectura(Magnitud.LUZ, luz),
                new Lectura(Magnitud.TEMPERATURA, 22));
    }

    private static SerieTemporal ayer(Instant ahora, IntToDoubleFunction lux) {
        List<Muestra> muestras = new ArrayList<>();
        for (int k = 1; k <= 96; k++) {
            muestras.add(new Muestra(ahora.minus(CUARTO.multipliedBy(k)), lux.applyAsDouble(k)));
        }
        return SerieTemporal.de(muestras);
    }

    /** 3 h a 1500 lux y el resto oscuro: unos 750 lux efectivos (cambian un poco con la lectura de ahora). */
    private static final IntToDoubleFunction POCA = k -> k <= 11 ? 1500 : 0;
    /** 6 h a 3000 lux y el resto oscuro: unos 3000 lux efectivos. */
    private static final IntToDoubleFunction DEMASIADA = k -> k <= 24 ? 3000 : 0;

    private Diagnostico evaluar(double luzAhora, IntToDoubleFunction serie) {
        return evaluador.evaluar(suculenta, medicion(20, luzAhora), MEDIODIA, ayer(MEDIODIA, serie));
    }

    @Test
    void bajoAcumuladoYLaLecturaDelMomentoTambienEstaBajaDiceDeAcercarlaYCuantaLuzLleva() {
        Diagnostico d = evaluar(500, POCA);

        assertThat(d.recomendaciones()).hasSize(1);
        assertThat(d.recomendaciones().get(0))
                .startsWith("Luz 708.3 lux efectivos")
                .contains(" lux efectivos por debajo del rango óptimo 800–2500 lux: "
                        + "acercarla a una fuente de luz indirecta; hoy lleva 3 h de luz.")
                .doesNotContain("500 lux por debajo");
    }

    @Test
    void bajoAcumuladoPeroLaLecturaDelMomentoYaAlcanzaDiceQueLaDejeAhi() {
        Diagnostico d = evaluar(1640, POCA);

        assertThat(d.recomendaciones()).hasSize(1);
        assertThat(d.recomendaciones().get(0)).isEqualTo(
                "Luz 755.8 lux efectivos por debajo del rango óptimo 800–2500 lux: "
                        + "ahora le llegan 1640 lux, déjala ahí para que sume la luz que le falta hoy.");
    }

    @Test
    void altoAcumuladoPeroLaLecturaDelMomentoYaEstaBienDiceQueLaMantenga() {
        Diagnostico d = evaluar(2000, DEMASIADA);

        assertThat(d.recomendaciones()).hasSize(1);
        assertThat(d.recomendaciones().get(0))
                .contains(" lux efectivos por encima del rango óptimo 800–2500 lux: "
                        + "ahora le llegan 2000 lux y está bien, mantenla así para que baje el promedio de hoy.");
    }

    @Test
    void altoAcumuladoYLaLecturaDelMomentoTambienAltaConservaLaAccionDeSiempre() {
        Diagnostico d = evaluar(3200, DEMASIADA);

        assertThat(d.recomendaciones()).hasSize(1);
        assertThat(d.recomendaciones().get(0))
                .contains(" lux efectivos por encima del rango óptimo 800–2500 lux: protegerla de la luz directa.");
    }

    @Test
    void ocupaLaPosicionDeLaLuzSiLaHumedadTambienFalla() {
        Diagnostico d = evaluador.evaluar(suculenta, medicion(50, 1640), MEDIODIA, ayer(MEDIODIA, POCA));

        assertThat(d.recomendaciones()).hasSize(2);
        assertThat(d.recomendaciones().get(0)).startsWith("Humedad del sustrato 50 %");
        assertThat(d.recomendaciones().get(1)).startsWith("Luz 755.8 lux efectivos");
    }

    @Test
    void sinHistorialLaLuzInstantaneaSigueComoSiempre() {
        Diagnostico d = evaluador.evaluar(suculenta, medicion(20, 500));

        assertThat(d.recomendaciones()).containsExactly(
                "Luz 500 lux por debajo del rango óptimo 800–2500 lux: acercarla a una fuente de luz indirecta.");
    }

    @Test
    void deNocheLaLuzNoDiceNada() {
        Diagnostico d = evaluador.evaluar(suculenta, medicion(20, 1640), DIEZ_PM, ayer(DIEZ_PM, POCA));

        assertThat(d.recomendaciones()).isEmpty();
    }

    @Test
    void numerosConUnDecimalYSinPuntoCeroEnEnteros() {
        var exposicion = new com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica(24, 0.4, 1640, 535.2, 0);
        var luz = new ReglaDeLuzAcumulada().clasificar(new Rango(800, 2500), 1640, exposicion);

        assertThat(new ReglaDeLuzAcumulada().recomendacion(luz, exposicion)).hasValue(
                "Luz 535.2 lux efectivos por debajo del rango óptimo 800–2500 lux: "
                        + "ahora le llegan 1640 lux, déjala ahí para que sume la luz que le falta hoy.");
        var lectBaja = new ReglaDeLuzAcumulada().clasificar(new Rango(800, 2500), 300, exposicion);
        assertThat(new ReglaDeLuzAcumulada().recomendacion(lectBaja, exposicion)).hasValue(
                "Luz 535.2 lux efectivos por debajo del rango óptimo 800–2500 lux: "
                        + "acercarla a una fuente de luz indirecta; hoy lleva 0.4 h de luz.");
    }
}
