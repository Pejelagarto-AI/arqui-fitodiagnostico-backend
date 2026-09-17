package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;

public final class EstadoOptimo implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Medicion medicion) {
        return true;
    }

    @Override
    public String obtenerEstado() {
        return "OPTIMO";
    }

    @Override
    public String describir(Especie especie, Medicion medicion) {
        return "las tres lecturas están dentro de los umbrales de la especie";
    }
}
