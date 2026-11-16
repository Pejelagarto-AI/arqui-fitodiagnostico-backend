package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;

import java.time.Instant;

/**
 * Lo que la mascota necesita de una lectura del historial: cuándo se tomó, humedad y luz. Sale de un
 * {@link DiagnosticoDePlanta} que ya existe; la mascota no guarda lecturas propias.
 */
public record Muestra(Instant tomadaEn, double humedad, double luz) {

    public static Muestra de(DiagnosticoDePlanta registro) {
        var medicion = registro.diagnostico().medicion();
        return new Muestra(registro.tomadaEn(), medicion.valor(Magnitud.HUMEDAD), medicion.valor(Magnitud.LUZ));
    }
}
