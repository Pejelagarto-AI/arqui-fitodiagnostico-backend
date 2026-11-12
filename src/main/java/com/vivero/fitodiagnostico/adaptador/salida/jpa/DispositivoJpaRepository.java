package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DispositivoJpaRepository extends JpaRepository<DispositivoEntity, String> {

    List<DispositivoEntity> findByPlantaIdOrderByCreadoEnAscSerialAsc(UUID plantaId);

    List<DispositivoEntity> findAllByOrderByCreadoEnAscSerialAsc();
}
