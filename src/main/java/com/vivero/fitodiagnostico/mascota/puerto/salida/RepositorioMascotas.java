package com.vivero.fitodiagnostico.mascota.puerto.salida;

import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeMascota;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El estado de cada mascota (tabla {@code mascota}, V16). */
public interface RepositorioMascotas {

    Optional<EstadoDeMascota> de(UUID plantaId);

    /** Igual que {@link #de}, pero bloquea la fila hasta el fin de la transacción: lectura y tarea periódica no se pisan. */
    Optional<EstadoDeMascota> paraActualizar(UUID plantaId);

    /** Varias mascotas en una consulta (fase 13, D185); solo las que tienen fila. */
    java.util.Map<UUID, EstadoDeMascota> deLasPlantas(java.util.Collection<UUID> plantaIds);

    void guardar(EstadoDeMascota estado);

    List<UUID> todas();
}
