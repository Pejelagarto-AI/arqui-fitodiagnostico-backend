package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.puerto.salida.AutenticadorDeDispositivos;

/** Doble sin criptografía: la respuesta correcta a un reto es {@code ok:<secreto>:<reto>}. */
public class AutenticadorDeDispositivosFalso implements AutenticadorDeDispositivos {

    public static String respuestaCorrecta(String secreto, String reto) {
        return "ok:" + secreto + ":" + reto;
    }

    @Override
    public boolean respuestaValida(String secreto, String reto, String respuesta) {
        return respuestaCorrecta(secreto, reto).equals(respuesta);
    }
}
