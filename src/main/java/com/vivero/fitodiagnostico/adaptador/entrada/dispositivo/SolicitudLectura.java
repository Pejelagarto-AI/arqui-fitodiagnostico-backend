package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Cuerpo que manda el dispositivo: cuándo midió (ISO-8601) y los tres valores. Bean Validation cubre
 * ausencia, Jackson cubre lo que no es número ni fecha, y lo físicamente imposible lo valida
 * {@link Lectura} al convertir: el adaptador solo traduce, nunca decide qué es una lectura válida.
 */
public record SolicitudLectura(

        @NotNull
        Instant marcaDeTiempo,

        @NotNull
        Double humedad,

        @NotNull
        Double luz,

        @NotNull
        Double temperatura) {

    /** Conversión a dominio en el borde, en el orden humedad, luz, temperatura del contrato. */
    public Medicion aMedicion() {
        return Medicion.de(
                new Lectura(Magnitud.HUMEDAD, humedad),
                new Lectura(Magnitud.LUZ, luz),
                new Lectura(Magnitud.TEMPERATURA, temperatura));
    }
}
