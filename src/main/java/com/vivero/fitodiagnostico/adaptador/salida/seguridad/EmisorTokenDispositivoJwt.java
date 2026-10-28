package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorTokenDispositivoPort;
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
import java.util.UUID;

/**
 * Adaptador de {@link EmisorTokenDispositivoPort}: un JWT HS256 de ALCANCE LIMITADO. Lleva
 * {@code tipo=dispositivo} (los tokens de usuario llevan {@code tipo=usuario}), {@code scope=lecturas:reportar},
 * {@code sub=<dispositivoId>} y {@code planta=<plantaId>}: lo único que el borde necesita para saber qué
 * dispositivo es, de qué planta y qué puede hacer. Misma clave y emisor que el token de usuario, pero el
 * borde rechaza uno donde se espera el otro.
 *
 * <p>Caducidad larga (por defecto un año, {@code FITO_JWT_DISPOSITIVO_EXPIRACION}) porque un ESP32 no
 * puede iniciar sesión. Costo asumido: sin revocación individual; revocar es esperar la caducidad o
 * rotar el secreto (que invalida todos los tokens). Ver docs/fase2/IMPLEMENTACION.md.
 */
@Component
public class EmisorTokenDispositivoJwt implements EmisorTokenDispositivoPort {

    public static final String TIPO = "tipo";
    public static final String TIPO_DISPOSITIVO = "dispositivo";
    public static final String SCOPE = "scope";
    public static final String ALCANCE_REPORTAR_LECTURAS = "lecturas:reportar";
    public static final String PLANTA = "planta";

    private final JwtEncoder codificador;
    private final Duration duracion;
    private final Clock reloj;

    @Autowired   // constructor, no campo (RA-09); hay un segundo constructor para pruebas con reloj fijo
    public EmisorTokenDispositivoJwt(JwtEncoder codificador,
                                     @Value("${fitodiagnostico.seguridad.jwt.dispositivo-expiracion:P365D}") Duration duracion) {
        this(codificador, duracion, Clock.systemUTC());
    }

    EmisorTokenDispositivoJwt(JwtEncoder codificador, Duration duracion, Clock reloj) {
        this.codificador = codificador;
        this.duracion = duracion;
        this.reloj = reloj;
    }

    @Override
    public TokenDeDispositivo emitir(String dispositivoId, UUID plantaId) {
        Instant ahora = reloj.instant();
        Instant expira = ahora.plus(duracion);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EmisorDeTokensJwt.EMISOR)
                .subject(dispositivoId)
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim(TIPO, TIPO_DISPOSITIVO)
                .claim(SCOPE, ALCANCE_REPORTAR_LECTURAS)
                .claim(PLANTA, plantaId.toString())
                .build();
        String valor = codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenDeDispositivo(dispositivoId, plantaId, valor, expira);
    }
}
