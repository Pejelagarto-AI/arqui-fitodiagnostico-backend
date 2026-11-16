package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los invariantes de los objetos de valor de la fase 8. */
class LuzAcumuladaEnElModeloTest {

    private static final Rango RANGO = new Rango(800, 2500);

    @Test
    void unaEvaluacionInstantaneaNoPuedeCompararOtroValorQueElLeido() {
        assertThatThrownBy(() -> new ResultadoParametro(Magnitud.LUZ, 300, RANGO, Clasificacion.BAJO,
                700, BaseDeEvaluacion.INSTANTANEA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laDesviacionSeMideSobreElValorEvaluadoNoSobreElLeido() {
        // Leyó 2000 (en rango) pero el acumulado es 630: desviación (800 - 630) / 1700 = 0,1.
        ResultadoParametro luz = new ResultadoParametro(Magnitud.LUZ, 2000, RANGO, Clasificacion.BAJO,
                630, BaseDeEvaluacion.ACUMULADA);

        assertThat(luz.desviacionRelativa()).isCloseTo(0.1, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void elConstructorDeCuatroArgumentosEsInstantaneo() {
        ResultadoParametro luz = new ResultadoParametro(Magnitud.LUZ, 300, RANGO, Clasificacion.BAJO);

        assertThat(luz.valorEvaluado()).isEqualTo(300);
        assertThat(luz.base()).isEqualTo(BaseDeEvaluacion.INSTANTANEA);
    }

    @Test
    void sinExposicionNoSePuedeAfirmarQueEsDeNoche() {
        Especie especie = new Especie("suculenta", Map.of(Magnitud.LUZ, RANGO));
        Medicion medicion = Medicion.de(new Lectura(Magnitud.LUZ, 0));

        assertThatThrownBy(() -> new Diagnostico(especie, medicion, EstadoGlobal.SALUDABLE, List.of(), List.of(),
                Instant.now(), Periodo.NOCHE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Diagnostico(especie, medicion, EstadoGlobal.SALUDABLE, List.of(), List.of(), Instant.now())
                .baseDeLuz()).isEqualTo(BaseDeEvaluacion.INSTANTANEA);
    }

    @Test
    void laExposicionNoAdmiteMasHorasDeLuzQueObservadas() {
        assertThatThrownBy(() -> new ExposicionLuminica(2, 3, 1000, 1000, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExposicionLuminica(2, 1, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laSerieSeOrdenaYRecortaPorVentanaSemiabierta() {
        Instant t0 = Instant.parse("2026-11-16T12:00:00Z");
        SerieTemporal serie = SerieTemporal.de(List.of(
                new Muestra(t0.plusSeconds(20), 3), new Muestra(t0, 1), new Muestra(t0.plusSeconds(10), 2)));

        assertThat(serie.muestras()).extracting(Muestra::valor).containsExactly(1.0, 2.0, 3.0);
        assertThat(serie.entre(t0, t0.plusSeconds(20))).extracting(Muestra::valor).containsExactly(1.0, 2.0);
    }
}
