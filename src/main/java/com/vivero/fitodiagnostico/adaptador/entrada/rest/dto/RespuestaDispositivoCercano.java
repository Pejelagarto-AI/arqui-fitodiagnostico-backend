package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.DescubrirDispositivosPort.Cercano;

/**
 * Una matera sin dueño que está cerca. NUNCA lleva el serial completo: ese serial es la prueba de que quien
 * reclama tiene la matera enfrente (lo lee de su pantalla), así que aquí solo se deja ver para reconocerla.
 */
public record RespuestaDispositivoCercano(String serialEnmascarado, long vistoHaceSegundos) {

    private static final String PREFIJO = "fito-";
    private static final int VISIBLES = 2;
    private static final String PUNTOS = "····";

    public static RespuestaDispositivoCercano desde(Cercano c) {
        return new RespuestaDispositivoCercano(enmascarar(c.dispositivo().serial()), c.vistoHaceSegundos());
    }

    /** {@code fito-YUBUD6} sale {@code fito-YU····}; un serial sin el prefijo conserva sus dos primeras letras. */
    static String enmascarar(String serial) {
        String base = serial.startsWith(PREFIJO) ? PREFIJO : "";
        String resto = serial.substring(base.length());
        // Con un resto de 2 caracteres o menos no se enseñaría nada oculto: se enseña solo el primero.
        int visibles = Math.min(VISIBLES, Math.max(1, resto.length() - 1));
        return base + resto.substring(0, Math.min(visibles, resto.length())) + PUNTOS;
    }
}
