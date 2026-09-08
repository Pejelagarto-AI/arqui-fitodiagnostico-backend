package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoNecesitaLuzTest {

    private final EstadoNecesitaLuz estado = new EstadoNecesitaLuz();

    // Monstera deliciosa: luz 1000 - 2500 lux
    private final Especie especie = new Especie(
            "Monstera deliciosa", "costilla de Adán",
            new Rango(18.0, 29.0), new Rango(55.0, 80.0), new Rango(1000, 2500));

    @Test
    void noAplicaExactamenteEnElMinimoDeLuz() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 1000);
        assertThat(estado.evaluarEstado(especie, ambiente)).isFalse();
    }

    @Test
    void aplicaJustoBajoElMinimoDeLuz() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 999);
        assertThat(estado.evaluarEstado(especie, ambiente)).isTrue();
    }

    @Test
    void noAplicaExactamenteEnElMaximoDeLuz() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 2500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isFalse();
    }

    @Test
    void obtenerEstadoDevuelveElNombreConstante() {
        assertThat(estado.obtenerEstado()).isEqualTo("NECESITA_LUZ");
    }

    @Test
    void describirIncluyeElValorLeidoYElMinimoDeLaEspecie() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 850);
        assertThat(estado.describir(especie, ambiente))
                .contains("850").contains("1000");
    }
}
