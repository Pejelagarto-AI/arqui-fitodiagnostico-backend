package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import com.vivero.fitodiagnostico.aplicacion.RutasDelFront;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dónde vive cada pantalla del front a la que apunta un correo (D53). La URL
 * base sale de {@code fitodiagnostico.front.url} ({@code FITO_FRONT_URL}); las
 * rutas son las de {@link RutasDelFront}, que es el único sitio donde están escritas.
 *
 * <p>Vive en el adaptador de notificación porque es configuración de despliegue que usa el
 * adaptador de correo. El dominio solo conoce el token (un dato del aviso).
 */
@Component
public class EnlacesDelFront {

    private final String base;

    public EnlacesDelFront(@Value("${fitodiagnostico.front.url:http://localhost:5500}") String base) {
        this.base = base;
    }

    /** Enlace que el invitado abre para aceptar (RF-E1, RF-E2). */
    public String invitacion(String token) {
        return RutasDelFront.absoluta(base, RutasDelFront.invitacion(token));
    }

    /** Enlace que recibe quien olvidó su contraseña (RF-A7). */
    public String restablecimiento(String token) {
        return RutasDelFront.absoluta(base, RutasDelFront.restablecimiento(token));
    }
}
