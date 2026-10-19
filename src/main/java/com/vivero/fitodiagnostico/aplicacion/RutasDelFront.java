package com.vivero.fitodiagnostico.aplicacion;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Las pantallas del front a las que apunta un enlace con token (D53): un acuerdo
 * con el front, escrito en UN solo sitio. Lo usan quienes arman enlaces absolutos
 * (el adaptador de correo, en infraestructura, y la respuesta de generar una
 * invitación, en web) sin que ninguno de los dos dependa del otro, ni la web
 * de un adaptador. La URL base es configuración de despliegue
 * ({@code fitodiagnostico.front.url}) y la pone cada uno.
 *
 * <p>Sin Spring ni estado: funciones puras sobre texto.
 */
public final class RutasDelFront {

    private RutasDelFront() { }

    /** Pantalla donde el invitado acepta (RF-E1, RF-E2): {@code /invitacion?token=...}. */
    public static String invitacion(String token) {
        return "/invitacion?token=" + codificar(token);
    }

    /** Pantalla donde quien olvidó su contraseña elige otra (RF-A7): {@code /restablecer?token=...}. */
    public static String restablecimiento(String token) {
        return "/restablecer?token=" + codificar(token);
    }

    /** Une la URL base (con o sin barra final) y una ruta de este tipo. */
    public static String absoluta(String base, String ruta) {
        String sinBarra = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return sinBarra + ruta;
    }

    private static String codificar(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
