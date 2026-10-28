package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenDeDispositivoTest {

    private static final Instant EXPIRA = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    void aceptaIdentificadoresComoEsp32Guion01() {
        TokenDeDispositivo token = new TokenDeDispositivo("esp32-01", UUID.randomUUID(), "x", EXPIRA);

        assertThat(token.dispositivoId()).isEqualTo("esp32-01");
    }

    @Test
    void rechazaIdentificadoresVaciosLargosOConCaracteresRaros() {
        UUID planta = UUID.randomUUID();
        assertThatThrownBy(() -> new TokenDeDispositivo(null, planta, "x", EXPIRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenDeDispositivo("", planta, "x", EXPIRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenDeDispositivo("a".repeat(65), planta, "x", EXPIRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenDeDispositivo("a/b", planta, "x", EXPIRA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exigePlantaValorYCaducidad() {
        assertThatThrownBy(() -> new TokenDeDispositivo("d", null, "x", EXPIRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenDeDispositivo("d", UUID.randomUUID(), " ", EXPIRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenDeDispositivo("d", UUID.randomUUID(), "x", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elToStringNoMuestraElValorDelToken() {
        assertThat(new TokenDeDispositivo("d", UUID.randomUUID(), "secreto-del-token", EXPIRA).toString())
                .doesNotContain("secreto-del-token");
    }
}
