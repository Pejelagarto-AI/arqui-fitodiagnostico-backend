package com.vivero.fitodiagnostico.infraestructura.persistencia;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DiagnosticoDePlantaJpaRepository extends JpaRepository<DiagnosticoDePlantaEntity, UUID> {

    Optional<DiagnosticoDePlantaEntity> findFirstByPlantaIdOrderByRegistradoEnDescIdDesc(UUID plantaId);

    Page<DiagnosticoDePlantaEntity> findByPlantaIdOrderByRegistradoEnDescIdDesc(UUID plantaId, Pageable pageable);

    // Una sola sentencia: las tablas hijas caen por ON DELETE CASCADE (V7). Cargar y borrar
    // entidad por entidad serían decenas de miles de DELETE para una planta con maceta.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DiagnosticoDePlantaEntity d where d.plantaId = :plantaId")
    void borrarDeLaPlanta(@Param("plantaId") UUID plantaId);
}
