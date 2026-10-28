package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;

/**
 * Puerto de entrada: evaluar una medición suelta contra una especie, sin planta
 * ni cuenta (RF-F6). No escribe nada.
 */
public interface DiagnosticarPort {

    /** @throws com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException si la especie no existe */
    Diagnostico diagnosticar(String nombreDeEspecie, Medicion medicion);
}
