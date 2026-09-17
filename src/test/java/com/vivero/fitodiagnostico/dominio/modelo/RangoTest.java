package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RangoTest {

    private final Rango rango = new Rango(10.0, 20.0);

    @Test
    void contieneElLimiteInferior() {
        assertThat(rango.contiene(10.0)).isTrue();
    }

    @Test
    void contieneElLimiteSuperior() {
        assertThat(rango.contiene(20.0)).isTrue();
    }

    @Test
    void contieneUnValorIntermedio() {
        assertThat(rango.contiene(15.0)).isTrue();
    }

    @Test
    void noContieneUnValorPorDebajoDelMinimo() {
        assertThat(rango.contiene(9.999)).isFalse();
    }

    @Test
    void noContieneUnValorPorEncimaDelMaximo() {
        assertThat(rango.contiene(20.001)).isFalse();
    }

    @Test
    void porDebajoEsFalsoExactamenteEnElMinimo() {
        assertThat(rango.porDebajo(10.0)).isFalse();
    }

    @Test
    void porDebajoEsVerdaderoJustoBajoElMinimo() {
        assertThat(rango.porDebajo(9.999)).isTrue();
    }

    @Test
    void porEncimaEsFalsoExactamenteEnElMaximo() {
        assertThat(rango.porEncima(20.0)).isFalse();
    }

    @Test
    void porEncimaEsVerdaderoJustoSobreElMaximo() {
        assertThat(rango.porEncima(20.001)).isTrue();
    }

    @Test
    void clasificaBajoCuandoElValorEstaPorDebajoDelMinimo() {
        assertThat(rango.clasificar(9.999)).isEqualTo(Clasificacion.BAJO);
    }

    @Test
    void clasificaOptimoExactamenteEnElMinimo() {
        assertThat(rango.clasificar(10.0)).isEqualTo(Clasificacion.OPTIMO);
    }

    @Test
    void clasificaOptimoUnValorIntermedio() {
        assertThat(rango.clasificar(15.0)).isEqualTo(Clasificacion.OPTIMO);
    }

    @Test
    void clasificaOptimoExactamenteEnElMaximo() {
        assertThat(rango.clasificar(20.0)).isEqualTo(Clasificacion.OPTIMO);
    }

    @Test
    void clasificaAltoCuandoElValorEstaPorEncimaDelMaximo() {
        assertThat(rango.clasificar(20.001)).isEqualTo(Clasificacion.ALTO);
    }

    @Test
    void unRangoInvertidoLanzaIllegalArgumentException() {
        assertThatThrownBy(() -> new Rango(20.0, 10.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unRangoConMinimoIgualAMaximoLanza() {
        assertThatThrownBy(() -> new Rango(15.0, 15.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
