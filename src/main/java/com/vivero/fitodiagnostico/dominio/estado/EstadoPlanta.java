package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;

/**
 * Estrategia de reconocimiento de estado. Implementaciones sin estado propio:
 * toda la información necesaria llega por parámetro.
 */
public interface EstadoPlanta {

    /** true si estas condiciones corresponden a este estado. */
    boolean evaluarEstado(Especie especie, Medicion medicion);

    /** Nombre del estado, tal como viaja en la respuesta. */
    String obtenerEstado();

    /** Explicación numérica de por qué aplica. */
    String describir(Especie especie, Medicion medicion);
}
