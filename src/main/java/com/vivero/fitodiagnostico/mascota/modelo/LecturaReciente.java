package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;

import java.time.Instant;

/**
 * Lo que la mascota necesita de una lectura del historial (no confundir con {@code dominio.modelo.Muestra} de la fase 8): cuándo se tomó, humedad y luz. Sale de un
 * {@link DiagnosticoDePlanta} que ya existe; la mascota no guarda lecturas propias.
 */
public record LecturaReciente(Instant tomadaEn, double humedad, double luz) {

    public static LecturaReciente de(DiagnosticoDePlanta registro) {
        var medicion = registro.diagnostico().medicion();
        return new LecturaReciente(registro.tomadaEn(), medicion.valor(Magnitud.HUMEDAD), medicion.valor(Magnitud.LUZ));
    }
}
