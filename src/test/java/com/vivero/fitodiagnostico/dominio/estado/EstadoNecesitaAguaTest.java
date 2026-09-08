package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoNecesitaAguaTest {

    private final EstadoNecesitaAgua estado = new EstadoNecesitaAgua();

    // Monstera deliciosa: humedad 55.0 - 80.0 % HR
    private final Especie especie = new Especie(
            "Monstera deliciosa", "costilla de Adán",
            new Rango(18.0, 29.0), new Rango(55.0, 80.0), new Rango(1000, 2500));

    @Test
    void noAplicaExactamenteEnElMinimoDeHumedad() {
        Ambiente ambiente = new Ambiente(20.0, 55.0, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isFalse();
    }

    @Test
    void aplicaJustoBajoElMinimoDeHumedad() {
        Ambiente ambiente = new Ambiente(20.0, 54.9, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isTrue();
    }

    @Test
    void noAplicaExactamenteEnElMaximoDeHumedad() {
        Ambiente ambiente = new Ambiente(20.0, 80.0, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isFalse();
    }

    @Test
    void obtenerEstadoDevuelveElNombreConstante() {
        assertThat(estado.obtenerEstado()).isEqualTo("NECESITA_AGUA");
    }

    @Test
    void describirIncluyeElValorLeidoYElMinimoDeLaEspecie() {
        Ambiente ambiente = new Ambiente(20.0, 42.0, 1500);
        assertThat(estado.describir(especie, ambiente))
                .contains("42.0").contains("55.0");
    }
}
