package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RestablecimientoJpaRepository extends JpaRepository<RestablecimientoEntity, UUID> {

    Optional<RestablecimientoEntity> findByHuellaToken(String huellaToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RestablecimientoEntity r where r.usuarioId = :usuarioId")
    void borrarDelUsuario(@Param("usuarioId") UUID usuarioId);
}
