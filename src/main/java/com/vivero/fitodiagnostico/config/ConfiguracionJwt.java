package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.adaptador.salida.seguridad.EmisorDeTokensJwt;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Firma y verificación de los JWT con HS256 (clave simétrica). Simétrica y no
 * RS256 porque quien emite y quien valida es el mismo proceso: no hay un
 * tercero que deba verificar sin poder firmar, que es lo único que justifica
 * un par de claves.
 *
 * <p>El secreto sale de {@code fitodiagnostico.seguridad.jwt.secreto}, que
 * {@code application.yml} enlaza a la variable de entorno
 * {@code FITO_JWT_SECRETO} SIN valor por defecto: si falta, la aplicación no
 * arranca. Solo el perfil {@code test} trae un valor de desarrollo.
 */
@Configuration
public class ConfiguracionJwt {

    /** HS256 exige una clave de al menos 256 bits (RFC 7518, 3.2). */
    private static final int BYTES_MINIMOS = 32;

    private final SecretKey clave;

    public ConfiguracionJwt(@Value("${fitodiagnostico.seguridad.jwt.secreto}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < BYTES_MINIMOS) {
            throw new IllegalStateException(
                "FITO_JWT_SECRETO debe tener al menos " + BYTES_MINIMOS + " bytes para firmar con HS256");
        }
        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(clave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Valida firma (implícito), caducidad y que el emisor sea este sistema.
        OAuth2TokenValidator<Jwt> validador = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(EmisorDeTokensJwt.EMISOR));
        decodificador.setJwtValidator(validador);
        return decodificador;
    }
}
