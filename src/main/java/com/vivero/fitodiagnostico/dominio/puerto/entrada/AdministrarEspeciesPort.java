package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;

import java.util.Map;

/** Puerto de entrada del administrador sobre el catálogo de especies (RF-C3 a RF-C6). */
public interface AdministrarEspeciesPort {

    Especie crearEspecie(String nombre, Map<Magnitud, Rango> rangos);

    Especie editarEspecie(String nombre, Map<Magnitud, Rango> rangos);

    Especie desactivarEspecie(String nombre);

    Especie reactivarEspecie(String nombre);
}
