package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;

public final class EstadoNecesitaAbrigo implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Medicion medicion) {
        return !especie.rango(Magnitud.TEMPERATURA).contiene(medicion.valor(Magnitud.TEMPERATURA));
    }

    @Override
    public String obtenerEstado() {
        return "NECESITA_ABRIGO";
    }

    @Override
    public String describir(Especie especie, Medicion medicion) {
        double temperaturaC = medicion.valor(Magnitud.TEMPERATURA);
        Rango rango = especie.rango(Magnitud.TEMPERATURA);
        if (rango.porDebajo(temperaturaC)) {
            return "temperatura " + temperaturaC
                 + " °C por debajo del mínimo " + rango.minimo() + " °C";
        }
        return "temperatura " + temperaturaC
             + " °C por encima del máximo " + rango.maximo() + " °C";
    }
}
