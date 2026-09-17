package com.vivero.fitodiagnostico.dominio.excepcion;

import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;

/**
 * Se lanza cuando una lectura ambiental es sintácticamente válida pero físicamente
 * imposible para el rango del sensor (por ejemplo, una humedad relativa negativa).
 * Expone la {@link Magnitud} que falló para que la capa web pueda reportar
 * {@code detalle.campo} sin volver a parsear el mensaje (RA6).
 */
public class LecturaInvalidaException extends RuntimeException {

    private final Magnitud magnitud;

    public LecturaInvalidaException(Magnitud magnitud, String mensaje) {
        super(mensaje);
        this.magnitud = magnitud;
    }

    public Magnitud getMagnitud() {
        return magnitud;
    }
}
