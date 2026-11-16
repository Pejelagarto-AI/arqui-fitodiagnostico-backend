package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ResumenLuzHoraJpaRepository extends JpaRepository<ResumenLuzHoraEntity, ResumenLuzHoraId> {

    /** Las horas {@code desde <= hora < hasta} de una planta; las sirve la clave primaria (planta_id, hora). */
    @Query("select r from ResumenLuzHoraEntity r where r.id.plantaId = :plantaId and r.id.hora >= :desde "
            + "and r.id.hora < :hasta order by r.id.hora")
    List<ResumenLuzHoraEntity> entre(@Param("plantaId") UUID plantaId, @Param("desde") Instant desde,
                                     @Param("hasta") Instant hasta);

    /** Una lectura atrasada invalida la hora que toca y la siguiente (su primer tramo depende de la última de esta). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResumenLuzHoraEntity r where r.id.plantaId = :plantaId and r.id.hora >= :desde "
            + "and r.id.hora <= :hasta")
    int borrarEntre(@Param("plantaId") UUID plantaId, @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
