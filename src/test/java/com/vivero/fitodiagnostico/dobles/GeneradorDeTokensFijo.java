package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.puerto.salida.GeneradorDeTokens;

/**
 * Doble de {@link GeneradorDeTokens} determinista: los tokens son
 * {@code token-1}, {@code token-2}... y la huella es {@code huella:<texto>}.
 * Legible en los fallos y sin azar, para que una prueba sepa qué token tiene.
 */
public class GeneradorDeTokensFijo implements GeneradorDeTokens {

    private int contador;

    @Override
    public String generar() {
        contador++;
        return "token-" + contador;
    }

    @Override
    public String huella(String texto) {
        return "huella:" + texto;
    }

    public String ultimo() {
        return "token-" + contador;
    }
}
