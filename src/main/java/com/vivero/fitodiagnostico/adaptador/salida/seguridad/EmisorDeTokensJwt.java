package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorDeTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Adaptador de {@link EmisorDeTokens}: un JWT HS256 cuyo único dato de usuario
 * es {@code sub} (su id). NO lleva roles ni correo: en cada petición el borde
 * web vuelve a leer al usuario, así que revocar el rol (RF-B5) o eliminar la
 * cuenta (RF-B4, RF-A6) surte efecto de inmediato en lugar de esperar a que el
 * token caduque. Ver docs/corte2/IMPLEMENTACION.md.
 */
@Component
public class EmisorDeTokensJwt implements EmisorDeTokens {

    public static final String EMISOR = "fitodiagnostico";

    private final JwtEncoder codificador;
    private final Duration duracion;
    private final Clock reloj;

    @Autowired   // constructor, no campo (RA-09); hay un segundo constructor para pruebas con reloj fijo
    public EmisorDeTokensJwt(JwtEncoder codificador,
                             @Value("${fitodiagnostico.seguridad.jwt.expiracion:PT2H}") Duration duracion) {
        this(codificador, duracion, Clock.systemUTC());
    }

    EmisorDeTokensJwt(JwtEncoder codificador, Duration duracion, Clock reloj) {
        this.codificador = codificador;
        this.duracion = duracion;
        this.reloj = reloj;
    }

    @Override
    public TokenDeSesion emitir(Usuario usuario) {
        Instant ahora = reloj.instant();
        Instant expira = ahora.plus(duracion);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(usuario.id().toString())
                .issuedAt(ahora)
                .expiresAt(expira)
                .build();
        String valor = codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenDeSesion(valor, expira);
    }
}
