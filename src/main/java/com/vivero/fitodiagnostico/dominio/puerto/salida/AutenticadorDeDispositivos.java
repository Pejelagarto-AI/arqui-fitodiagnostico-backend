package com.vivero.fitodiagnostico.dominio.puerto.salida;

/**
 * Puerto de salida: "¿esta respuesta demuestra que quien contesta conoce el secreto?". Cómo se
 * calcula (hoy HMAC-SHA256) y cómo se compara (en tiempo constante) es tecnología del adaptador; el
 * dominio solo pregunta.
 */
public interface AutenticadorDeDispositivos {

    boolean respuestaValida(String secreto, String reto, String respuesta);

    /**
     * ¿La firma demuestra que quien se registra conoce la clave de la flota? La clave la conoce solo el
     * adaptador; el dominio nunca la ve. Falso si la flota no está configurada.
     */
    boolean firmaDeFlotaValida(String serial, String secreto, String firma);
}
