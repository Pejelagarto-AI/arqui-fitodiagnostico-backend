package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;
import java.util.Objects;

/** Un valor leído para una magnitud concreta. Inmutable. */
public record Lectura(Magnitud magnitud, double valor) {

    public Lectura {
        Objects.requireNonNull(magnitud, "la lectura requiere una magnitud");
        if (!magnitud.rangoFisico().contiene(valor)) {
            throw new LecturaInvalidaException(magnitud,
                magnitud.descripcion() + " " + valor + " " + magnitud.unidad()
                    + " fuera del rango físico del sensor");
        }
    }
}
