package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoOptimoTest {

    private final EstadoOptimo estado = new EstadoOptimo();

    private final Especie especie = new Especie(
            "Monstera deliciosa", "costilla de Adán",
            new Rango(18.0, 29.0), new Rango(55.0, 80.0), new Rango(1000, 2500));

    @Test
    void esTerminalYSiempreAplica() {
        // Incluso con lecturas fuera de todos los umbrales, es el único estado
        // garantizado a devolver true — la cadena depende de eso.
        Ambiente ambiente = new Ambiente(-10.0, 5.0, 10);
        assertThat(estado.evaluarEstado(especie, ambiente)).isTrue();
    }

    @Test
    void aplicaConLecturasDentroDeTodosLosUmbrales() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, ambiente)).isTrue();
    }

    @Test
    void obtenerEstadoDevuelveElNombreConstante() {
        assertThat(estado.obtenerEstado()).isEqualTo("OPTIMO");
    }

    @Test
    void describirNoDependeDeLosValoresRecibidos() {
        Ambiente ambiente = new Ambiente(20.0, 60.0, 1500);
        assertThat(estado.describir(especie, ambiente)).isNotBlank();
    }
}
