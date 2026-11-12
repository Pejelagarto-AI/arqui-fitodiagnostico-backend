package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredencialTest {

    @Test
    void sinHashNoHayCredencial() {
        assertThatThrownBy(() -> new Credencial(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Credencial(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNoImprimeElHash() {
        assertThat(new Credencial("secreto").toString()).doesNotContain("secreto");
    }

    @Test
    void dosCredencialesConElMismoHashSonIguales() {
        assertThat(new Credencial("x")).isEqualTo(new Credencial("x"));
    }
}
