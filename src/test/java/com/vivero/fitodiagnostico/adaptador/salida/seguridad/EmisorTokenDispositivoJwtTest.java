package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import com.vivero.fitodiagnostico.config.ConfiguracionJwt;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El adaptador JWT del puerto de dispositivos: qué lleva el token y que lo valida el mismo decodificador. */
class EmisorTokenDispositivoJwtTest {

    private static final String SECRETO = "secreto-solo-para-pruebas-no-usar-en-produccion-0123456789";
    private static final Instant AHORA = Instant.parse("2026-10-19T12:00:00Z");

    private final UUID planta = UUID.randomUUID();

    private EmisorTokenDispositivoJwt emisor(Duration duracion, Clock reloj) {
        return new EmisorTokenDispositivoJwt(new ConfiguracionJwt(SECRETO).jwtEncoder(), duracion, reloj);
    }

    private JwtDecoder decodificador() {
        return new ConfiguracionJwt(SECRETO).jwtDecoder();
    }

    @Test
    void elTokenLlevaDispositivoPlantaYUnAlcanceLimitadoAReportarLecturas() {
        TokenDeDispositivo token = emisor(Duration.ofDays(365), Clock.fixed(AHORA, ZoneOffset.UTC))
                .emitir("esp32-01", planta);

        Jwt jwt = decodificador().decode(token.valor());

        assertThat(jwt.getSubject()).isEqualTo("esp32-01");
        assertThat(jwt.getClaimAsString("tipo")).isEqualTo("dispositivo");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("lecturas:reportar");
        assertThat(jwt.getClaimAsString("planta")).isEqualTo(planta.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("fitodiagnostico");
    }

    @Test
    void laCaducidadEsLaConfiguradaYLaDevuelveElToken() {
        TokenDeDispositivo token = emisor(Duration.ofDays(365), Clock.fixed(AHORA, ZoneOffset.UTC))
                .emitir("esp32-01", planta);

        assertThat(token.expiraEn()).isEqualTo(AHORA.plus(Duration.ofDays(365)));
        assertThat(token.plantaId()).isEqualTo(planta);
        assertThat(token.dispositivoId()).isEqualTo("esp32-01");
    }

    @Test
    void unTokenCaducadoSeRechaza() {
        Clock haceDosDias = Clock.fixed(AHORA.minus(Duration.ofDays(2)), ZoneOffset.UTC);
        TokenDeDispositivo viejo = emisor(Duration.ofDays(1), haceDosDias).emitir("esp32-01", planta);

        assertThatThrownBy(() -> decodificador().decode(viejo.valor())).isInstanceOf(JwtException.class);
    }
}
