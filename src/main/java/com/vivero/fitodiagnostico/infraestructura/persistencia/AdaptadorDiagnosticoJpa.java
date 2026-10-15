package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioDiagnosticos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA del puerto {@link RepositorioDiagnosticos}. {@code Pageable} de
 * Spring Data vive solo aquí: el dominio habla de {@link Paginacion}.
 */
@Repository
public class AdaptadorDiagnosticoJpa implements RepositorioDiagnosticos {

    private final DiagnosticoDePlantaJpaRepository jpa;
    private final MapeadorDiagnosticoDePlanta mapeador;

    public AdaptadorDiagnosticoJpa(DiagnosticoDePlantaJpaRepository jpa, MapeadorDiagnosticoDePlanta mapeador) {
        this.jpa = jpa;
        this.mapeador = mapeador;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiagnosticoDePlanta> ultimoDe(UUID plantaId) {
        return jpa.findFirstByPlantaIdOrderByRegistradoEnDescIdDesc(plantaId).map(mapeador::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<DiagnosticoDePlanta> historialDe(UUID plantaId, Paginacion paginacion) {
        Page<DiagnosticoDePlantaEntity> pagina = jpa.findByPlantaIdOrderByRegistradoEnDescIdDesc(
                plantaId, PageRequest.of(paginacion.pagina(), paginacion.tamano()));
        return new Pagina<>(pagina.getContent().stream().map(mapeador::aDominio).toList(),
                paginacion.pagina(), paginacion.tamano(), pagina.getTotalElements());
    }

    @Override
    @Transactional
    public void guardar(DiagnosticoDePlanta diagnostico) {
        DiagnosticoDePlantaEntity entidad = jpa.findById(diagnostico.id()).orElseGet(mapeador::nuevaEntidad);
        mapeador.volcarEn(entidad, diagnostico);
        jpa.saveAndFlush(entidad);
    }

    @Override
    @Transactional
    public void eliminarDe(UUID plantaId) {
        jpa.borrarDeLaPlanta(plantaId);
    }
}
