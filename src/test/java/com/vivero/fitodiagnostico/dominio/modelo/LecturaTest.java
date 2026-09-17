package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LecturaTest {

    @Test
    void aceptaLosLimitesExactosDelSensorDeTemperatura() {
        assertThat(new Lectura(Magnitud.TEMPERATURA, -20.0).valor()).isEqualTo(-20.0);
        assertThat(new Lectura(Magnitud.TEMPERATURA, 60.0).valor()).isEqualTo(60.0);
    }

    @Test
    void aceptaLosLimitesExactosDelSensorDeHumedad() {
        assertThat(new Lectura(Magnitud.HUMEDAD, 0.0).valor()).isEqualTo(0.0);
        assertThat(new Lectura(Magnitud.HUMEDAD, 100.0).valor()).isEqualTo(100.0);
    }

    @Test
    void aceptaLosLimitesExactosDelSensorDeLuz() {
        assertThat(new Lectura(Magnitud.LUZ, 0.0).valor()).isEqualTo(0.0);
        assertThat(new Lectura(Magnitud.LUZ, 150_000.0).valor()).isEqualTo(150_000.0);
    }

    @Test
    void temperaturaPorDebajoDelRangoFisicoLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Lectura(Magnitud.TEMPERATURA, -20.1))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void temperaturaPorEncimaDelRangoFisicoLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Lectura(Magnitud.TEMPERATURA, 60.1))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void humedadNegativaLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Lectura(Magnitud.HUMEDAD, -0.1))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void humedadSobreCienLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Lectura(Magnitud.HUMEDAD, 100.1))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void luzNegativaLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Lectura(Magnitud.LUZ, -1))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void luzSobreElMaximoDelSensorLanzaLecturaInvalida() {
        assertThatThrownBy(() -> new Lectura(Magnitud.LUZ, 150_001))
                .isInstanceOf(LecturaInvalidaException.class);
    }

    @Test
    void magnitudNulaLanzaNullPointerException() {
        assertThatThrownBy(() -> new Lectura(null, 20.0))
                .isInstanceOf(NullPointerException.class);
    }
}
