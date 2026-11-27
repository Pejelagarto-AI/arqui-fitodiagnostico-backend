package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.BaseDeEvaluacion;
import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
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
import java.util.Map;
import java.util.function.IntToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

/** El evaluador con la regla de luz de la fase 8: el caso de las 10 pm que motivó el cambio. */
class EvaluadorConLuzAcumuladaTest {

    private static final Instant DIEZ_PM = Instant.parse("2026-11-17T03:00:00Z");   // 10 pm en Bogotá
    private static final Duration CUARTO = Duration.ofMinutes(15);

    // Suculenta del catálogo: humedad 10-30 %, luz 800-2500 lux, temperatura 15-32 °C.
    private final Especie suculenta = new Especie("suculenta", Map.of(
            Magnitud.HUMEDAD, new Rango(10, 30),
            Magnitud.LUZ, new Rango(800, 2500),
            Magnitud.TEMPERATURA, new Rango(15, 32)));

    private final EvaluadorDeEstado evaluador = new EvaluadorDeEstado(new ClasificadorDeParametros(),
            new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO), new RedactorDeRecomendaciones());

    private static Medicion medicion(double humedad, double luz, double temperatura) {
        return Medicion.de(new Lectura(Magnitud.HUMEDAD, humedad), new Lectura(Magnitud.LUZ, luz),
                new Lectura(Magnitud.TEMPERATURA, temperatura));
    }

    private static SerieTemporal unDiaAntesDe(Instant ahora, IntToDoubleFunction lux) {
        List<Muestra> muestras = new ArrayList<>();
        for (int k = 1; k <= 96; k++) {
            muestras.add(new Muestra(ahora.minus(CUARTO.multipliedBy(k)), lux.applyAsDouble(k)));
        }
        return SerieTemporal.de(muestras);
    }

    private static ResultadoParametro luz(Diagnostico diagnostico) {
        return diagnostico.parametros().stream().filter(p -> p.magnitud() == Magnitud.LUZ).findFirst().orElseThrow();
    }

    @Test
    void sinHistorialSeConservaLaClasificacionInstantaneaYLoDice() {
        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 0, 22));

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);   // 0 lux, como en el corte 1
        assertThat(diagnostico.periodo()).isEqualTo(Periodo.DIA);
        assertThat(diagnostico.exposicion()).isNull();
        assertThat(diagnostico.baseDeLuz()).isEqualTo(BaseDeEvaluacion.INSTANTANEA);
        assertThat(luz(diagnostico).base()).isEqualTo(BaseDeEvaluacion.INSTANTANEA);
    }

    @Test
    void conLaSerieVaciaEsLoMismoQueSinHistorialSiEsDeDia() {
        Instant mediodia = Instant.parse("2026-11-16T17:00:00Z");   // 12:00 en Bogotá

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 0, 22), mediodia, SerieTemporal.vacia());

        assertThat(diagnostico.baseDeLuz()).isEqualTo(BaseDeEvaluacion.INSTANTANEA);
        assertThat(diagnostico.periodo()).isEqualTo(Periodo.DIA);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);
    }

    @Test
    void sinHistorialPeroDeNocheLaLuzNoCuentaPorqueElPeriodoSalePorElReloj() {
        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 0, 22), DIEZ_PM, SerieTemporal.vacia());

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.NOCHE);
        assertThat(diagnostico.exposicion()).isNull();
        assertThat(diagnostico.baseDeLuz()).isEqualTo(BaseDeEvaluacion.INSTANTANEA);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
    }

    @Test
    void deDiaUnRinconOscuroSigueBajoAunqueLleveHorasSinLuz() {
        // 14:00 en Bogotá, 6 h a 20 lux: antes era NOCHE y nadie avisaba; ahora es DIA con luz BAJO.
        Instant dosPm = Instant.parse("2026-11-16T19:00:00Z");
        SerieTemporal oscuro = unDiaAntesDe(dosPm, k -> 20);

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 20, 22), dosPm, oscuro);

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.DIA);
        assertThat(luz(diagnostico).clasificacion()).isEqualTo(Clasificacion.BAJO);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);
        assertThat(diagnostico.recomendaciones()).hasSize(1);
    }

    @Test
    void deNocheUnaLamparaNoLaVuelveDiaYLaLuzNoCuenta() {
        // 23:00 en Bogotá, lámpara encendida desde hace 3 h (300 lux): sigue siendo NOCHE.
        Instant once = Instant.parse("2026-11-17T04:00:00Z");
        SerieTemporal lampara = unDiaAntesDe(once, k -> k <= 12 ? 300 : 5);

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 300, 22), once, lampara);

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.NOCHE);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
    }

    @Test
    void aLasDiezDeLaNocheTrasUnBuenDiaLaPlantaDuermeSaludable() {
        // Sol de 6 am a 5 pm (k = 21 a 64, 11 h a 1500 lux); desde las 5 pm, oscuridad.
        SerieTemporal dia = unDiaAntesDe(DIEZ_PM, k -> k >= 21 && k <= 64 ? 1500 : 5);

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 0, 22), DIEZ_PM, dia);

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.NOCHE);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
        assertThat(diagnostico.recomendaciones()).isEmpty();
        assertThat(diagnostico.baseDeLuz()).isEqualTo(BaseDeEvaluacion.ACUMULADA);
        assertThat(luz(diagnostico).clasificacion()).isEqualTo(Clasificacion.OPTIMO);
        assertThat(luz(diagnostico).valor()).isZero();   // la lectura del momento no se pierde
    }

    @Test
    void deNocheUnMalDiaNoDegradaElEstadoPeroElParametroLoSigueDiciendo() {
        // Solo 2 h de sol en todo el día: la luz acumulada es BAJO, pero de noche no cuenta.
        SerieTemporal diaGris = unDiaAntesDe(DIEZ_PM, k -> k >= 21 && k <= 28 ? 1500 : 5);

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 0, 22), DIEZ_PM, diaGris);

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.NOCHE);
        assertThat(luz(diagnostico).clasificacion()).isEqualTo(Clasificacion.BAJO);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
        assertThat(diagnostico.recomendaciones()).isEmpty();
    }

    @Test
    void deNocheLasOtrasMagnitudesSiguenContando() {
        SerieTemporal dia = unDiaAntesDe(DIEZ_PM, k -> k >= 21 && k <= 64 ? 1500 : 5);

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(35, 0, 22), DIEZ_PM, dia);

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.NOCHE);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.EN_RIESGO);   // humedad 35 > 30
        assertThat(diagnostico.recomendaciones()).hasSize(1).first().asString().startsWith("Humedad");
    }

    @Test
    void deDiaConPocaExposicionLaLuzCuentaYLaRecomendacionHablaDelPromedio() {
        Instant mediodia = Instant.parse("2026-11-16T17:00:00Z");
        // 3 h a 1500 lux en el día: 1500 x 3 / 6 = 750 lux efectivos, por debajo de 800.
        SerieTemporal poca = unDiaAntesDe(mediodia, k -> k <= 11 ? 1500 : 0);

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 1500, 22), mediodia, poca);

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.DIA);
        assertThat(luz(diagnostico).clasificacion()).isEqualTo(Clasificacion.BAJO);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.EN_RIESGO);
        assertThat(diagnostico.recomendaciones()).containsExactly(
                "Luz 750 lux de promedio en el día, por debajo del rango óptimo 800–2500 lux: "
                        + "acercarla a una fuente de luz indirecta.");
    }

    @Test
    void deDiaConBuenaExposicionUnaNubeNoLaPoneEnRiesgo() {
        Instant tarde = Instant.parse("2026-11-16T20:00:00Z");
        SerieTemporal buena = unDiaAntesDe(tarde, k -> k <= 40 ? 1500 : 5);

        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(20, 300, 22), tarde, buena);

        assertThat(diagnostico.periodo()).isEqualTo(Periodo.DIA);
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
    }
}
