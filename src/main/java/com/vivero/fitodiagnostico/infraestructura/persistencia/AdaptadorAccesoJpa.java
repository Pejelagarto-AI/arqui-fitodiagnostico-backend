package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioAccesos;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Adaptador JPA del puerto {@link RepositorioAccesos}. */
@Repository
public class AdaptadorAccesoJpa implements RepositorioAccesos {

    private final AccesoJpaRepository jpa;

    public AdaptadorAccesoJpa(AccesoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> invitadosDe(UUID plantaId) {
        return jpa.findByIdPlantaId(plantaId).stream()
                .map(acceso -> acceso.getId().invitadoId())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccesoCompartido> accesosDe(UUID plantaId) {
        return jpa.findByIdPlantaIdOrderByOtorgadoEnAscIdInvitadoIdAsc(plantaId).stream()
                .map(a -> new AccesoCompartido(a.getId().plantaId(), a.getId().invitadoId(), a.getOtorgadoEn()))
                .toList();
    }

    @Override
    @Transactional
    public void otorgar(AccesoCompartido acceso) {
        AccesoId id = new AccesoId(acceso.plantaId(), acceso.invitadoId());
        if (!jpa.existsById(id)) {
            jpa.saveAndFlush(new AccesoEntity(id, acceso.otorgadoEn()));
        }
    }

    @Override
    @Transactional
    public void revocar(UUID plantaId, UUID invitadoId) {
        AccesoId id = new AccesoId(plantaId, invitadoId);
        if (jpa.existsById(id)) {
            jpa.deleteById(id);
            jpa.flush();
        }
    }

    @Override
    @Transactional
    public void revocarTodosDe(UUID plantaId) {
        jpa.borrarDeLaPlanta(plantaId);
    }

    @Override
    @Transactional
    public void revocarTodosDelInvitado(UUID invitadoId) {
        jpa.borrarDelInvitado(invitadoId);
    }
}
