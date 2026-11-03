package com.vivero.fitodiagnostico.dominio.puerto.salida;

/**
 * Puerto de salida: "¿esta respuesta demuestra que quien contesta conoce el secreto?". Cómo se
 * calcula (hoy HMAC-SHA256) y cómo se compara (en tiempo constante) es tecnología del adaptador; el
 * dominio solo pregunta.
 */
public interface AutenticadorDeDispositivos {

    boolean respuestaValida(String secreto, String reto, String respuesta);
}
