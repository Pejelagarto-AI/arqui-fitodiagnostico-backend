package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginacionTest {

    @Test
    void rechazaPaginasNegativasYTamanosFueraDeRango() {
        assertThatThrownBy(() -> new Paginacion(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paginacion(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paginacion(0, Paginacion.TAMANO_MAXIMO + 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Paginacion(0, Paginacion.TAMANO_MAXIMO)).isNotNull();
    }

    @Test
    void calculaElDesplazamientoSinDesbordarEnteros() {
        assertThat(new Paginacion(3, 20).desplazamiento()).isEqualTo(60);
        assertThat(new Paginacion(Integer.MAX_VALUE, 100).desplazamiento()).isEqualTo(214_748_364_700L);
    }

    @Test
    void laPaginaCalculaCuantasHay() {
        assertThat(new Pagina<>(List.of(1, 2), 0, 2, 5).totalDePaginas()).isEqualTo(3);
        assertThat(new Pagina<>(List.of(), 0, 20, 0).totalDePaginas()).isZero();
        assertThat(new Pagina<>(List.of(1), 0, 1, 1).totalDePaginas()).isEqualTo(1);
    }
}
