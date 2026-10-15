package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;

/**
 * Puerto de escritura del catálogo de especies (D8). Separado de
 * {@link CatalogoDeEspecies} y {@link RangosPorEspecie} (ISP): el diagnóstico
 * solo lee, y darle además la capacidad de modificar especies sería entregarle
 * un poder que no usa y que alguien podría usar por error.
 *
 * <p>No hay operación de borrado a propósito: las especies se desactivan,
 * nunca se eliminan (D10).
 */
public interface CatalogoEditable {

    /**
     * Crea la especie si no existe (por nombre, sin distinguir mayúsculas) o
     * reemplaza sus rangos y su estado si ya existe. Decidir si el llamador
     * quería crear o editar es de la capa de aplicación, que antes consulta
     * {@link RangosPorEspecie}.
     */
    void guardar(Especie especie);

    /**
     * Activa o desactiva la especie de ese nombre (RF-C5, RF-C6). Si no existe
     * no hace nada: la aplicación comprueba la existencia antes de llamar.
     */
    void cambiarActivacion(String nombre, boolean activa);
}
