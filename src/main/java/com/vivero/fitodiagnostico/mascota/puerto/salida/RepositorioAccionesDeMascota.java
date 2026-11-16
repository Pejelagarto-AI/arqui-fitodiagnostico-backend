package com.vivero.fitodiagnostico.mascota.puerto.salida;

import com.vivero.fitodiagnostico.mascota.modelo.AccionDeMascota;

import java.util.Optional;
import java.util.UUID;

/** Los "la regué" (tabla {@code accion_mascota}, V18). A lo sumo uno PENDIENTE por planta. */
public interface RepositorioAccionesDeMascota {

    void guardar(AccionDeMascota accion);

    Optional<AccionDeMascota> pendienteDe(UUID plantaId);
}
