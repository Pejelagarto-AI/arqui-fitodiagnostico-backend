package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import com.vivero.fitodiagnostico.dominio.puerto.salida.AutenticadorDeDispositivos;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>Fase 3b: también verifica la firma de flota de un auto-registro,
 * {@code base64( HMAC-SHA256( clave = bytes UTF-8 de la clave de flota, mensaje = serial + "\n" + secreto ) )}.
 * La clave de flota llega por configuración ({@code fitodiagnostico.flota.clave}); vacía significa "sin flota"
 * y ninguna firma vale. Si viene, debe tener al menos 32 caracteres o la aplicación no arranca.
 */
@Component
public class AutenticadorHmacSha256 implements AutenticadorDeDispositivos {

    private static final String ALGORITMO = "HmacSHA256";
    private static final int LARGO_MINIMO_DE_LA_CLAVE_DE_FLOTA = 32;

    private final String claveDeFlota;

    public AutenticadorHmacSha256(@Value("${fitodiagnostico.flota.clave:}") String claveDeFlota) {
        String clave = claveDeFlota == null ? "" : claveDeFlota;
        if (!clave.isEmpty() && clave.length() < LARGO_MINIMO_DE_LA_CLAVE_DE_FLOTA) {
            throw new IllegalStateException("FITO_CLAVE_FLOTA debe tener al menos "
                    + LARGO_MINIMO_DE_LA_CLAVE_DE_FLOTA + " caracteres (o quedar vacía para no usar la flota)");
        }
        this.claveDeFlota = clave;
    }

    @Override
    public boolean respuestaValida(String secreto, String reto, String respuesta) {
        if (secreto == null || reto == null || respuesta == null) {
            return false;
        }
        byte[] esperada = Base64.getEncoder().encode(calcular(secreto, reto));
        return MessageDigest.isEqual(esperada, respuesta.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean firmaDeFlotaValida(String serial, String secreto, String firma) {
        if (claveDeFlota.isEmpty() || serial == null || secreto == null || firma == null) {
            return false;
        }
        byte[] esperada = Base64.getEncoder().encode(calcular(claveDeFlota, serial + "\n" + secreto));
        return MessageDigest.isEqual(esperada, firma.getBytes(StandardCharsets.UTF_8));
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
