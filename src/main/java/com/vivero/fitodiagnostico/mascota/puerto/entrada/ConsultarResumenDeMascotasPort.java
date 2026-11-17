package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import com.vivero.fitodiagnostico.mascota.modelo.ResumenDeMascota;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * El resumen de muchas mascotas a la vez, para el tablero de plantas (fase 13, D185). Sin autorización: los ids
 * salen de un listado que ya filtró lo que el solicitante puede ver. Cuesta un número fijo de consultas, no una por planta.
 */
public interface ConsultarResumenDeMascotasPort {

    /** Solo las plantas que ya tienen fila de mascota; las demás no aparecen en el mapa. */
    Map<UUID, ResumenDeMascota> deLasPlantas(Collection<UUID> plantaIds);
}
