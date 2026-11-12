package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlantaJpaRepository extends JpaRepository<PlantaEntity, UUID> {

    List<PlantaEntity> findByDuenoIdOrderByRegistradaEnDescIdDesc(UUID duenoId);

    @Query("select p from PlantaEntity p, AccesoEntity a "
         + "where a.id.plantaId = p.id and a.id.invitadoId = :usuario "
         + "order by p.registradaEn desc, p.id desc")
    List<PlantaEntity> compartidasCon(@Param("usuario") UUID usuario);
}
