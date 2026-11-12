package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioInvitaciones;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del puerto {@link RepositorioInvitaciones}. */
@Repository
public class AdaptadorInvitacionJpa implements RepositorioInvitaciones {

    private final InvitacionJpaRepository jpa;

    public AdaptadorInvitacionJpa(InvitacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void guardar(Invitacion i) {
        jpa.saveAndFlush(new InvitacionEntity(i.id(), i.plantaId(), i.huellaDelToken(), i.estado(),
                i.creadaEn(), i.expiraEn(), i.correoDestino(), i.aceptadaEn()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invitacion> porHuella(String huellaDelToken) {
        return jpa.findByHuellaToken(huellaDelToken).map(AdaptadorInvitacionJpa::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invitacion> buscar(UUID id) {
        return jpa.findById(id).map(AdaptadorInvitacionJpa::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invitacion> pendientesDe(UUID plantaId) {
        return jpa.findByPlantaIdAndEstadoOrderByCreadaEnDescIdDesc(plantaId, EstadoInvitacion.PENDIENTE).stream()
                .map(AdaptadorInvitacionJpa::aDominio).toList();
    }

    @Override
    @Transactional
    public void eliminarDe(UUID plantaId) {
        jpa.borrarDeLaPlanta(plantaId);
    }

    private static Invitacion aDominio(InvitacionEntity e) {
        return new Invitacion(e.getId(), e.getPlantaId(), e.getHuellaToken(), e.getEstado(), e.getCreadaEn(),
                e.getExpiraEn(), e.getCorreoDestino(), e.getAceptadaEn());
    }
}
