package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import java.util.Optional;

/**
 * Puerto de salida que necesita el diagnóstico (RA-05): dado el nombre de una
 * especie, sus rangos de tolerancia. Separado de {@link CatalogoDeEspecies}
 * (ISP): el caso de uso de diagnóstico no tiene por qué depender de la
 * operación de listado que solo necesita el catálogo.
 */
public interface RangosPorEspecie {

    Optional<Especie> buscar(String nombre);
}
