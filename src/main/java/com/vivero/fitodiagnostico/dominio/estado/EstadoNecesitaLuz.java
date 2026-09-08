package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;

public final class EstadoNecesitaLuz implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Ambiente ambiente) {
        return especie.luz().porDebajo(ambiente.luzLux());
    }

    @Override
    public String obtenerEstado() {
        return "NECESITA_LUZ";
    }

    @Override
    public String describir(Especie especie, Ambiente ambiente) {
        return "iluminancia " + ambiente.luzLux()
             + " lux por debajo del mínimo " + especie.luz().minimo() + " lux";
    }
}
