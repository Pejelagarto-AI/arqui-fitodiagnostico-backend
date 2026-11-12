package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;

import java.util.UUID;

/** Puerto de entrada: alta, edición y baja de plantas (RF-D1, RF-D4, RF-D5). */
public interface GestionarPlantasPort {

    Planta registrar(UUID duenoId, String apodo, String nombreDeEspecie);

    Planta editar(UUID solicitante, UUID plantaId, String apodo, String nombreDeEspecie);

    void eliminar(UUID solicitante, UUID plantaId);
}
