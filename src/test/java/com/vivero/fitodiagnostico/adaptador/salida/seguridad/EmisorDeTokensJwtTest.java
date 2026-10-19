package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import com.vivero.fitodiagnostico.config.ConfiguracionJwt;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El emisor y el decodificador, probados juntos y SIN contexto de Spring:
 * lo que firma uno lo valida el otro, y solo eso. Cubre las tres razones por
 * las que un token debe rechazarse: caducado, firmado con otra clave y
 * emitido por otro sistema.
 */
class EmisorDeTokensJwtTest {

    private static final String SECRETO = "secreto-solo-para-pruebas-no-usar-en-produccion-0123456789";
    private static final Instant AHORA = Instant.now();

    private final Usuario ana = Usuario.nuevo("Ana", "ana@vivero.co", new Credencial("h")).conRolAdministrador();

    private EmisorDeTokensJwt emisor(String secreto, Clock reloj) {
        return new EmisorDeTokensJwt(new ConfiguracionJwt(secreto).jwtEncoder(), Duration.ofHours(2), reloj);
    }

    private JwtDecoder decodificador(String secreto) {
        return new ConfiguracionJwt(secreto).jwtDecoder();
    }

    @Test
    void elTokenEmitidoSeValidaYSoloLlevaElIdDelUsuario() {
        TokenDeSesion token = emisor(SECRETO, Clock.fixed(AHORA, ZoneOffset.UTC)).emitir(ana);

        Jwt jwt = decodificador(SECRETO).decode(token.valor());

        assertThat(jwt.getSubject()).isEqualTo(ana.id().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("fitodiagnostico");
        // Sin roles, correo ni nombre en el token: se leen de la base en cada petición. Solo el tipo.
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "tipo");
        assertThat(jwt.getClaimAsString("tipo")).isEqualTo("usuario");
    }

    @Test
    void laCaducidadEsLaConfigurada() {
        TokenDeSesion token = emisor(SECRETO, Clock.fixed(AHORA, ZoneOffset.UTC)).emitir(ana);

        assertThat(token.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(2)));
    }

    @Test
    void unTokenCaducadoSeRechaza() {
        Clock haceTresHoras = Clock.fixed(AHORA.minus(Duration.ofHours(3)), ZoneOffset.UTC);
        TokenDeSesion viejo = emisor(SECRETO, haceTresHoras).emitir(ana);

        assertThatThrownBy(() -> decodificador(SECRETO).decode(viejo.valor())).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String otroSecreto = "otro-secreto-distinto-también-de-32-bytes-o-mas-xxxxxxxx";
        TokenDeSesion ajeno = emisor(otroSecreto, Clock.fixed(AHORA, ZoneOffset.UTC)).emitir(ana);

        assertThatThrownBy(() -> decodificador(SECRETO).decode(ajeno.valor())).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenManipuladoSeRechaza() {
        TokenDeSesion token = emisor(SECRETO, Clock.fixed(AHORA, ZoneOffset.UTC)).emitir(ana);
        String[] partes = token.valor().split("\\.");
        String alterado = partes[0] + "." + partes[1] + "x." + partes[2];

        assertThatThrownBy(() -> decodificador(SECRETO).decode(alterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void unSecretoCortoImpideArrancar() {
        assertThatThrownBy(() -> new ConfiguracionJwt("corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FITO_JWT_SECRETO");
    }
}
