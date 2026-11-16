package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import java.util.List;
import java.util.UUID;

/**
 * La tarea periódica (D141): el tiempo pasa aunque no lleguen lecturas. La vitalidad baja en CRITICO, la racha suma
 * días y los "la regué" vencen. Una planta por llamada, para que una que falle no deshaga las otras.
 */
public interface AvanzarMascotasPort {

    List<UUID> mascotasActivas();

    void avanzar(UUID plantaId);
}
