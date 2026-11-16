package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlantaJpaRepository extends JpaRepository<PlantaEntity, UUID> {

    // Fase 12 (D177): la especie viene en la misma consulta. Con el EAGER solo, la lista de N plantas hacía además una
    // consulta por cada especie distinta (1 + N).
    @EntityGraph(attributePaths = "especie")
    List<PlantaEntity> findByDuenoIdOrderByRegistradaEnDescIdDesc(UUID duenoId);

    @Query("select p from PlantaEntity p join fetch p.especie, AccesoEntity a "
         + "where a.id.plantaId = p.id and a.id.invitadoId = :usuario "
         + "order by p.registradaEn desc, p.id desc")
    List<PlantaEntity> compartidasCon(@Param("usuario") UUID usuario);
}
