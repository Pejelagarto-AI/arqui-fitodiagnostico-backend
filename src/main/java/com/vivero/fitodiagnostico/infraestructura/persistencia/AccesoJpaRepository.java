package com.vivero.fitodiagnostico.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AccesoJpaRepository extends JpaRepository<AccesoEntity, AccesoId> {

    List<AccesoEntity> findByIdPlantaId(UUID plantaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccesoEntity a where a.id.plantaId = :plantaId")
    void borrarDeLaPlanta(@Param("plantaId") UUID plantaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccesoEntity a where a.id.invitadoId = :invitadoId")
    void borrarDelInvitado(@Param("invitadoId") UUID invitadoId);
}
