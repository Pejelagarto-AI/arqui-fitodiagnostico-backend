package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedicionTest {

    @Test
    void guardaYDevuelveElValorDeCadaMagnitud() {
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.TEMPERATURA, 20.0),
                new Lectura(Magnitud.HUMEDAD, 60.0),
                new Lectura(Magnitud.LUZ, 1500));

        assertThat(medicion.valor(Magnitud.TEMPERATURA)).isEqualTo(20.0);
        assertThat(medicion.valor(Magnitud.HUMEDAD)).isEqualTo(60.0);
        assertThat(medicion.valor(Magnitud.LUZ)).isEqualTo(1500.0);
    }

    @Test
    void unaMedicionVaciaLanza() {
        assertThatThrownBy(() -> Medicion.de())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Medicion.de(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dosLecturasDeLaMismaMagnitudLanzan() {
        assertThatThrownBy(() -> Medicion.de(
                new Lectura(Magnitud.TEMPERATURA, 20.0),
                new Lectura(Magnitud.TEMPERATURA, 25.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valorDeUnaMagnitudAusenteLanza() {
        Medicion medicion = Medicion.de(new Lectura(Magnitud.TEMPERATURA, 20.0));
        assertThatThrownBy(() -> medicion.valor(Magnitud.HUMEDAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contieneDistingueLasMagnitudesCargadas() {
        Medicion medicion = Medicion.de(new Lectura(Magnitud.TEMPERATURA, 20.0));
        assertThat(medicion.contiene(Magnitud.TEMPERATURA)).isTrue();
        assertThat(medicion.contiene(Magnitud.HUMEDAD)).isFalse();
    }

    @Test
    void lecturasSeDevuelveEnOrdenDelEnum() {
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.LUZ, 1500),
                new Lectura(Magnitud.TEMPERATURA, 20.0),
                new Lectura(Magnitud.HUMEDAD, 60.0));

        // Orden del enum Magnitud: HUMEDAD, LUZ, TEMPERATURA.
        assertThat(medicion.lecturas())
                .extracting(Lectura::magnitud)
                .containsExactly(Magnitud.HUMEDAD, Magnitud.LUZ, Magnitud.TEMPERATURA);
    }
}
