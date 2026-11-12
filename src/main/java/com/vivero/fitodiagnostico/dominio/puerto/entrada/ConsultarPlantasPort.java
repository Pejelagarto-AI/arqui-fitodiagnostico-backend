package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;

import java.util.List;
import java.util.UUID;

/** Puerto de entrada: consultar plantas (RF-D2, RF-D3). */
public interface ConsultarPlantasPort {

    List<Planta> listarPropias(UUID duenoId);

    /** Dueño o invitado; para cualquier otro la planta no existe. */
    Planta detalle(UUID solicitante, UUID plantaId);
}
