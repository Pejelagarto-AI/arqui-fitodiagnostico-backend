package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioRestablecimientos;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del puerto {@link RepositorioRestablecimientos}. */
@Repository
public class AdaptadorRestablecimientoJpa implements RepositorioRestablecimientos {

    private final RestablecimientoJpaRepository jpa;

    public AdaptadorRestablecimientoJpa(RestablecimientoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void guardar(SolicitudRestablecimiento s) {
        jpa.saveAndFlush(new RestablecimientoEntity(s.id(), s.usuarioId(), s.huellaDelToken(),
                s.huellaDeCredencial(), s.creadaEn(), s.expiraEn(), s.usado()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SolicitudRestablecimiento> porHuella(String huellaDelToken) {
        return jpa.findByHuellaToken(huellaDelToken).map(e -> new SolicitudRestablecimiento(e.getId(),
                e.getUsuarioId(), e.getHuellaToken(), e.getHuellaCredencial(), e.getCreadaEn(), e.getExpiraEn(),
                e.isUsado()));
    }

    @Override
    @Transactional
    public void eliminarDe(UUID usuarioId) {
        jpa.borrarDelUsuario(usuarioId);
    }
}
