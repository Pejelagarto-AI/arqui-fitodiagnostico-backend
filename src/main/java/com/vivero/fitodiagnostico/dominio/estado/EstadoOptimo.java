package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;

public final class EstadoOptimo implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Ambiente ambiente) {
        return true;
    }

    @Override
    public String obtenerEstado() {
        return "OPTIMO";
    }

    @Override
    public String describir(Especie especie, Ambiente ambiente) {
        return "las tres lecturas están dentro de los umbrales de la especie";
    }
}
