package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoNecesitaAbrigoTest {

    private final EstadoNecesitaAbrigo estado = new EstadoNecesitaAbrigo();

    // Monstera deliciosa: temperatura 18.0 - 29.0 °C
    private final Especie especie = new Especie(
            "Monstera deliciosa", "costilla de Adán",
            new Rango(18.0, 29.0), new Rango(55.0, 80.0), new Rango(1000, 2500));

    @Test
    void noAplicaExactamenteEnElMinimoDeTemperatura() {
        Ambiente ambiente = new Ambiente(18.0, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isFalse();
    }

    @Test
    void noAplicaExactamenteEnElMaximoDeTemperatura() {
        Ambiente ambiente = new Ambiente(29.0, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isFalse();
    }

    @Test
    void aplicaJustoBajoElMinimoDeTemperatura() {
        Ambiente ambiente = new Ambiente(17.9, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isTrue();
    }

    @Test
    void aplicaJustoSobreElMaximoDeTemperatura() {
        Ambiente ambiente = new Ambiente(29.1, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isTrue();
    }

    @Test
    void obtenerEstadoDevuelveElNombreConstante() {
        assertThat(estado.obtenerEstado()).isEqualTo("NECESITA_ABRIGO");
    }

    @Test
    void describirDistingueCuandoEstaPorDebajoDelMinimo() {
        Ambiente ambiente = new Ambiente(10.0, 60.0, 1500);
        assertThat(estado.describir(especie, ambiente))
                .contains("10.0").contains("por debajo").contains("18.0");
    }

    @Test
    void describirDistingueCuandoEstaPorEncimaDelMaximo() {
        Ambiente ambiente = new Ambiente(35.0, 60.0, 1500);
        assertThat(estado.describir(especie, ambiente))
                .contains("35.0").contains("por encima").contains("29.0");
    }
}
