package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitacionJpaRepository extends JpaRepository<InvitacionEntity, UUID> {

    Optional<InvitacionEntity> findByHuellaToken(String huellaToken);

    List<InvitacionEntity> findByPlantaIdAndEstadoOrderByCreadaEnDescIdDesc(UUID plantaId, EstadoInvitacion estado);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from InvitacionEntity i where i.plantaId = :plantaId")
    void borrarDeLaPlanta(@Param("plantaId") UUID plantaId);
}
