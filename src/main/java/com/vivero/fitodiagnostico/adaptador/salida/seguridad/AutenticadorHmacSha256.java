package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import com.vivero.fitodiagnostico.dominio.puerto.salida.AutenticadorDeDispositivos;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Adaptador de {@link AutenticadorDeDispositivos}: la respuesta correcta a un reto es
 * {@code base64( HMAC-SHA256( clave = bytes UTF-8 del string secreto, mensaje = bytes UTF-8 del string reto ) )}.
 *
 * <p>La clave es el STRING del secreto tal cual, no sus bytes decodificados, y el mensaje es el STRING del
 * reto tal cual: así el ESP32 hace {@code mbedtls_md_hmac} sobre dos cadenas ASCII sin decodificar nada
 * (D74). La comparación es en tiempo constante ({@link MessageDigest#isEqual}) para no filtrar por tiempo
 * cuántos caracteres de la respuesta acertó quien adivina.
 */
@Component
public class AutenticadorHmacSha256 implements AutenticadorDeDispositivos {

    private static final String ALGORITMO = "HmacSHA256";

    @Override
    public boolean respuestaValida(String secreto, String reto, String respuesta) {
        if (secreto == null || reto == null || respuesta == null) {
            return false;
        }
        byte[] esperada = Base64.getEncoder().encode(calcular(secreto, reto));
        return MessageDigest.isEqual(esperada, respuesta.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] calcular(String secreto, String reto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(reto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 es obligatorio en toda JVM", e);
        }
    }
}
