package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;

import java.time.Instant;

/**
 * Lo que la mascota necesita de una lectura del historial (no confundir con {@code dominio.modelo.Muestra} de la fase 8): cuándo se tomó, humedad, luz y si el diagnóstico de esa lectura era de día o de noche (D170). Sale de un
 * {@link DiagnosticoDePlanta} que ya existe; la mascota no guarda lecturas propias.
 */
public record LecturaReciente(Instant tomadaEn, double humedad, double luz, Periodo periodo) {

    /** Una lectura de día: lo que era antes de la fase 12 (D170). */
    public LecturaReciente(Instant tomadaEn, double humedad, double luz) {
        this(tomadaEn, humedad, luz, Periodo.DIA);
    }

    public static LecturaReciente de(DiagnosticoDePlanta registro) {
        var medicion = registro.diagnostico().medicion();
        return new LecturaReciente(registro.tomadaEn(), medicion.valor(Magnitud.HUMEDAD), medicion.valor(Magnitud.LUZ),
                registro.diagnostico().periodo());
    }
}
