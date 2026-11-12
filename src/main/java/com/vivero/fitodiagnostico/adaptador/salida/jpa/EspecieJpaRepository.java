package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EspecieJpaRepository extends JpaRepository<EspecieEntity, Long> {

    Optional<EspecieEntity> findByNombreIgnoreCase(String nombre);

    List<EspecieEntity> findAllByOrderByNombreAsc();
}
