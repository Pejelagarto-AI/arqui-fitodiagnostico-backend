package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosticoDePlantaJpaRepository extends JpaRepository<DiagnosticoDePlantaEntity, UUID> {

    Optional<DiagnosticoDePlantaEntity> findFirstByPlantaIdOrderByRegistradoEnDescIdDesc(UUID plantaId);

    Page<DiagnosticoDePlantaEntity> findByPlantaIdOrderByRegistradoEnDescIdDesc(UUID plantaId, Pageable pageable);

    // Fase 8 (D127): solo el instante y el valor de una magnitud, sin cargar diagnósticos completos.
    // Una matera a 5 s son unas 17 000 filas por día; las sirve ix_diagnostico_serie (V15).
    @Query("select d.tomadaEn, p.valor from DiagnosticoDePlantaEntity d join d.parametros p "
            + "where d.plantaId = :plantaId and p.magnitud = :magnitud "
            + "and d.tomadaEn >= :desde and d.tomadaEn < :hasta order by d.tomadaEn")
    List<Object[]> serie(@Param("plantaId") UUID plantaId, @Param("magnitud") Magnitud magnitud,
                         @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    // Una sola sentencia: las tablas hijas caen por ON DELETE CASCADE (V7). Cargar y borrar
    // entidad por entidad serían decenas de miles de DELETE para una planta con maceta.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DiagnosticoDePlantaEntity d where d.plantaId = :plantaId")
    void borrarDeLaPlanta(@Param("plantaId") UUID plantaId);
}
