package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;

public final class EstadoNecesitaAgua implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Medicion medicion) {
        return especie.rango(Magnitud.HUMEDAD).porDebajo(medicion.valor(Magnitud.HUMEDAD));
    }

    @Override
    public String obtenerEstado() {
        return "NECESITA_AGUA";
    }

    @Override
    public String describir(Especie especie, Medicion medicion) {
        return "humedad relativa " + medicion.valor(Magnitud.HUMEDAD)
             + " % por debajo del mínimo " + especie.rango(Magnitud.HUMEDAD).minimo() + " %";
    }
}
