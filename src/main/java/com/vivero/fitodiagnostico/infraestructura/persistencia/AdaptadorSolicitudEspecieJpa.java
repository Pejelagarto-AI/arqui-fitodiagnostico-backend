package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoSolicitud;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioSolicitudes;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del puerto {@link RepositorioSolicitudes}. */
@Repository
public class AdaptadorSolicitudEspecieJpa implements RepositorioSolicitudes {

    private final SolicitudEspecieJpaRepository jpa;

    public AdaptadorSolicitudEspecieJpa(SolicitudEspecieJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void guardar(SolicitudEspecie s) {
        jpa.saveAndFlush(new SolicitudEspecieEntity(s.id(), s.solicitanteId(), s.nombrePropuesto(),
                s.justificacion(), s.estado(), s.respuesta(), s.creadaEn(), s.resueltaEn()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SolicitudEspecie> buscar(UUID id) {
        return jpa.findById(id).map(AdaptadorSolicitudEspecieJpa::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudEspecie> pendientes() {
        return jpa.findByEstadoOrderByCreadaEnAscIdAsc(EstadoSolicitud.PENDIENTE).stream()
                .map(AdaptadorSolicitudEspecieJpa::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudEspecie> deSolicitante(UUID solicitanteId) {
        return jpa.findBySolicitanteIdOrderByCreadaEnDescIdDesc(solicitanteId).stream()
                .map(AdaptadorSolicitudEspecieJpa::aDominio).toList();
    }

    @Override
    @Transactional
    public void eliminarDe(UUID solicitanteId) {
        jpa.borrarDelSolicitante(solicitanteId);
    }

    private static SolicitudEspecie aDominio(SolicitudEspecieEntity e) {
        return new SolicitudEspecie(e.getId(), e.getSolicitanteId(), e.getNombrePropuesto(), e.getJustificacion(),
                e.getEstado(), e.getRespuesta(), e.getCreadaEn(), e.getResueltaEn());
    }
}
