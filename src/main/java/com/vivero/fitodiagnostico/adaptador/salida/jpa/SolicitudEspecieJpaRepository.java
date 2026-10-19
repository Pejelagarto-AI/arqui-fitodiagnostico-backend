package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SolicitudEspecieJpaRepository extends JpaRepository<SolicitudEspecieEntity, UUID> {

    List<SolicitudEspecieEntity> findByEstadoOrderByCreadaEnAscIdAsc(EstadoSolicitud estado);

    List<SolicitudEspecieEntity> findBySolicitanteIdOrderByCreadaEnDescIdDesc(UUID solicitanteId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SolicitudEspecieEntity s where s.solicitanteId = :solicitanteId")
    void borrarDelSolicitante(@Param("solicitanteId") UUID solicitanteId);
}
