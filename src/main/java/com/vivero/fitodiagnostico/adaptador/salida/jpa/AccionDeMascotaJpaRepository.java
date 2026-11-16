package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeAccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccionDeMascotaJpaRepository extends JpaRepository<AccionDeMascotaEntity, UUID> {

    /** Usa ix_accion_mascota_estado (V19). */
    Optional<AccionDeMascotaEntity> findFirstByPlantaIdAndEstadoOrderByCreadaEnDesc(UUID plantaId, EstadoDeAccion estado);
}
