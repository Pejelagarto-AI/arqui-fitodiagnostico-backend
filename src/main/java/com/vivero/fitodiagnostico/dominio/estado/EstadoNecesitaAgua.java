package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;

public final class EstadoNecesitaAgua implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Ambiente ambiente) {
        return especie.humedad().porDebajo(ambiente.humedadRelativa());
    }

    @Override
    public String obtenerEstado() {
        return "NECESITA_AGUA";
    }

    @Override
    public String describir(Especie especie, Ambiente ambiente) {
        return "humedad relativa " + ambiente.humedadRelativa()
             + " % por debajo del mínimo " + especie.humedad().minimo() + " %";
    }
}
