package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;

import java.util.List;

/** Puerto de entrada: el listado público de especies activas (RF5). */
public interface ConsultarEspeciesPort {

    List<Especie> listar();
}
