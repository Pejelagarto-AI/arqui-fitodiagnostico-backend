package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import java.util.List;

/**
 * Puerto de salida que necesita el listado de especies soportadas (RF5).
 * Separado de {@link RangosPorEspecie} (ISP): listar todas las especies es
 * una necesidad distinta a buscar los rangos de una sola.
 */
public interface CatalogoDeEspecies {

    List<Especie> listar();
}
