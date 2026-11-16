package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MascotaJpaRepository extends JpaRepository<MascotaEntity, UUID> {

    /** SELECT ... FOR UPDATE: la lectura y el tic no se pisan la misma mascota. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MascotaEntity m where m.plantaId = :plantaId")
    Optional<MascotaEntity> bloquear(@Param("plantaId") UUID plantaId);

    @Query("select m.plantaId from MascotaEntity m")
    List<UUID> plantas();
}
