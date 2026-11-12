package com.vivero.fitodiagnostico.adaptador.salida.seguridad;

import com.vivero.fitodiagnostico.dominio.puerto.salida.GeneradorDeTokens;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de un solo uso con {@link SecureRandom}: 32 bytes (256 bits) en
 * Base64 sin relleno y apto para URL (43 caracteres). Con esa entropía no hay
 * nada que adivinar ni enumerar.
 *
 * <p>La huella es SHA-256 en hexadecimal (64 caracteres). Un hash simple,
 * sin sal y sin costo, es correcto AQUÍ y no lo sería para una contraseña:
 * la entrada es aleatoria de 256 bits, no un texto que un humano eligió, así
 * que un ataque por diccionario o por fuerza bruta no tiene por dónde empezar
 * y BCrypt solo agregaría latencia a cada aceptación (D51). Para la huella de
 * la credencial (D52) la entrada ya es un hash BCrypt, no una contraseña.
 */
@Component
public class GeneradorDeTokensSeguro implements GeneradorDeTokens {

    private static final int BYTES_DE_ENTROPIA = 32;

    private final SecureRandom azar = new SecureRandom();

    @Override
    public String generar() {
        byte[] bytes = new byte[BYTES_DE_ENTROPIA];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String huella(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("no se puede calcular la huella de un texto nulo");
        }
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", e);
        }
    }
}
