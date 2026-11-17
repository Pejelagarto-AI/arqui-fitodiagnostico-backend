package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosticoDePlantaJpaRepository extends JpaRepository<DiagnosticoDePlantaEntity, UUID> {

    Optional<DiagnosticoDePlantaEntity> findFirstByPlantaIdOrderByRegistradoEnDescIdDesc(UUID plantaId);

    // Fase 13 (D185): el último diagnóstico de cada planta de un lote, en una sentencia (las colecciones EAGER vienen
    // en lote por SUBSELECT, no una consulta por diagnóstico). El empate de registrado_en se resuelve en el adaptador.
    @Query("select d from DiagnosticoDePlantaEntity d where d.plantaId in :plantaIds and d.registradoEn = "
            + "(select max(u.registradoEn) from DiagnosticoDePlantaEntity u where u.plantaId = d.plantaId)")
    List<DiagnosticoDePlantaEntity> ultimosDe(@Param("plantaIds") Collection<UUID> plantaIds);

    // Fase 12: ¿hay alguna lectura de la planta antes de ese instante? (ix_diagnostico_serie)
    boolean existsByPlantaIdAndTomadaEnBefore(UUID plantaId, Instant instante);

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

    // Fase 12 (D176): retención. "Las plantas con crudas viejas" usa ix_diagnostico_serie por planta (exists), no un distinct
    // sobre toda la tabla.
    @Query("select p.id from PlantaEntity p where exists (select 1 from DiagnosticoDePlantaEntity d "
            + "where d.plantaId = p.id and d.tomadaEn < :corte)")
    List<UUID> plantasConLecturasAnterioresA(@Param("corte") Instant corte);

    @Query("select min(d.tomadaEn) from DiagnosticoDePlantaEntity d where d.plantaId = :plantaId")
    Instant primeraLecturaDe(@Param("plantaId") UUID plantaId);

    // Una sentencia: las tablas hijas caen por ON DELETE CASCADE (V7).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DiagnosticoDePlantaEntity d where d.plantaId = :plantaId and d.tomadaEn >= :desde "
            + "and d.tomadaEn < :hasta")
    int borrarAnterioresA(@Param("plantaId") UUID plantaId, @Param("desde") Instant desde,
                          @Param("hasta") Instant hasta);
}
