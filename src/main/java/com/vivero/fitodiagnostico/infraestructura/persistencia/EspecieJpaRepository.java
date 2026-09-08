package com.vivero.fitodiagnostico.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EspecieJpaRepository extends JpaRepository<EspecieEntity, Long> {

    Optional<EspecieEntity> findByNombreCientificoIgnoreCase(String nombreCientifico);
}
