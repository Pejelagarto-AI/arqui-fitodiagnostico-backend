package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmbienteTest {

    @Test
    void aceptaLosLimitesExactosDelSensorDeTemperatura() {
        assertThat(new Ambiente(-20.0, 50.0, 500).temperaturaC()).isEqualTo(-20.0);
        assertThat(new Ambiente(60.0, 50.0, 500).temperaturaC()).isEqualTo(60.0);
    }

    @Test
    void aceptaLosLimitesExactosDelSensorDeHumedad() {
        assertThat(new Ambiente(20.0, 0.0, 500).humedadRelativa()).isEqualTo(0.0);
        assertThat(new Ambiente(20.0, 100.0, 500).humedadRelativa()).isEqualTo(100.0);
    }

    @Test
    void aceptaLosLimitesExactosDelSensorDeLuz() {
        assertThat(new Ambiente(20.0, 50.0, 0).luzLux()).isEqualTo(0);
        assertThat(new Ambiente(20.0, 50.0, 150_000).luzLux()).isEqualTo(150_000);
    }

    @Test
    void temperaturaPorDebajoDelRangoFisicoLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Ambiente(-20.1, 50.0, 500))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void temperaturaPorEncimaDelRangoFisicoLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Ambiente(60.1, 50.0, 500))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void humedadNegativaLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Ambiente(20.0, -0.1, 500))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void humedadSobreCienLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Ambiente(20.0, 100.1, 500))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void luzNegativaLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Ambiente(20.0, 50.0, -1))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void luzSobreElMaximoDelSensorLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Ambiente(20.0, 50.0, 150_001))
                .isInstanceOf(LecturaInvalidaException.class);
    }
}
