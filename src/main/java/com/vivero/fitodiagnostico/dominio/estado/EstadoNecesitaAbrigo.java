package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;

public final class EstadoNecesitaAbrigo implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Ambiente ambiente) {
        return !especie.temperatura().contiene(ambiente.temperaturaC());
    }

    @Override
    public String obtenerEstado() {
        return "NECESITA_ABRIGO";
    }

    @Override
    public String describir(Especie especie, Ambiente ambiente) {
        double temperaturaC = ambiente.temperaturaC();
        if (especie.temperatura().porDebajo(temperaturaC)) {
            return "temperatura " + temperaturaC
                 + " °C por debajo del mínimo " + especie.temperatura().minimo() + " °C";
        }
        return "temperatura " + temperaturaC
             + " °C por encima del máximo " + especie.temperatura().maximo() + " °C";
    }
}
