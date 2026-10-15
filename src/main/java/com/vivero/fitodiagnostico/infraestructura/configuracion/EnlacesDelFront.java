package com.vivero.fitodiagnostico.infraestructura.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Dónde vive cada pantalla del front a la que apunta un correo o una
 * respuesta con enlace (D53). La URL base sale de {@code fitodiagnostico.front.url}
 * ({@code FITO_FRONT_URL}); las rutas son un acuerdo con el front: la
 * pantalla de aceptar invitaciones es {@code /invitacion?token=...} y la de
 * restablecer contraseña, {@code /restablecer?token=...}.
 *
 * <p>Vive en infraestructura porque es configuración de despliegue. El
 * dominio solo conoce el token (un dato del aviso); armar la URL es del
 * borde que la entrega.
 */
@Component
public class EnlacesDelFront {

    private final String base;

    public EnlacesDelFront(@Value("${fitodiagnostico.front.url:http://localhost:5500}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Enlace que el invitado abre para aceptar (RF-E1, RF-E2). */
    public String invitacion(String token) {
        return base + "/invitacion?token=" + codificar(token);
    }

    /** Enlace que recibe quien olvidó su contraseña (RF-A7). */
    public String restablecimiento(String token) {
        return base + "/restablecer?token=" + codificar(token);
    }

    private static String codificar(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
