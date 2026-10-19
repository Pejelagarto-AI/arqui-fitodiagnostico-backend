package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los tokens de un solo uso (D51): impredecibles, aptos para URL y con una huella estable. */
class GeneradorDeTokensSeguroTest {

    private final GeneradorDeTokensSeguro generador = new GeneradorDeTokensSeguro();

    @Test
    void generaTokensDe256BitsAptosParaUnaUrl() {
        String token = generador.generar();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void dosMilTokensNoSeRepiten() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            vistos.add(generador.generar());
        }
        assertThat(vistos).hasSize(2000);
    }

    @Test
    void laHuellaEsSha256EnHexadecimalYDeterminista() {
        // SHA-256("abc"), valor de referencia estándar.
        assertThat(generador.huella("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(generador.huella("abc")).isEqualTo(generador.huella("abc"));
        assertThat(generador.huella("abc")).isNotEqualTo(generador.huella("abd"));
    }

    @Test
    void laHuellaNoContieneElTokenYTieneLargoFijo() {
        String token = generador.generar();

        assertThat(generador.huella(token)).hasSize(64).doesNotContain(token);
    }

    @Test
    void noCalculaLaHuellaDeNulo() {
        assertThatThrownBy(() -> generador.huella(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
