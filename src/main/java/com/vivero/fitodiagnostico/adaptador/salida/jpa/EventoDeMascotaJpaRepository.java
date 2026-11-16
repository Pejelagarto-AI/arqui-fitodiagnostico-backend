package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventoDeMascotaJpaRepository extends JpaRepository<EventoDeMascotaEntity, UUID> {

    /** Usa ix_evento_mascota_linea (V19). A igual hora, el registrado después primero. */
    Page<EventoDeMascotaEntity> findByPlantaIdOrderByOcurridoEnDescRegistradoEnDesc(UUID plantaId, Pageable pagina);

    /** Usa ix_evento_mascota_tipo (V22). */
    Optional<EventoDeMascotaEntity> findFirstByPlantaIdAndTipoOrderByOcurridoEnDescRegistradoEnDesc(UUID plantaId,
                                                                                                    TipoDeEvento tipo);
}
