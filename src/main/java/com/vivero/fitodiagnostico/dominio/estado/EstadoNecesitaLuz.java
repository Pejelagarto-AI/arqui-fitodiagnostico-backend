package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;

public final class EstadoNecesitaLuz implements EstadoPlanta {

    @Override
    public boolean evaluarEstado(Especie especie, Medicion medicion) {
        return especie.rango(Magnitud.LUZ).porDebajo(medicion.valor(Magnitud.LUZ));
    }

    @Override
    public String obtenerEstado() {
        return "NECESITA_LUZ";
    }

    @Override
    public String describir(Especie especie, Medicion medicion) {
        // La lectura de luz viaja por el sistema como entero (RF-08 / contrato HTTP);
        // se trunca aquí solo para el texto, igual que antes del refactor.
        int luzLux = (int) medicion.valor(Magnitud.LUZ);
        return "iluminancia " + luzLux
             + " lux por debajo del mínimo " + especie.rango(Magnitud.LUZ).minimo() + " lux";
    }
}
