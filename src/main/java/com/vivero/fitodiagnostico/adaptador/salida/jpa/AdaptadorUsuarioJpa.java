package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.modelo.Rol;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioUsuarios;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del puerto {@link RepositorioUsuarios}. */
@Repository
public class AdaptadorUsuarioJpa implements RepositorioUsuarios {

    /** Nombre del constraint de V5; aparece en el mensaje de H2 y de PostgreSQL. */
    private static final String CONSTRAINT_CORREO_UNICO = "uq_usuario_correo";

    private final UsuarioJpaRepository jpa;
    private final MapeadorUsuario mapeador;

    public AdaptadorUsuarioJpa(UsuarioJpaRepository jpa, MapeadorUsuario mapeador) {
        this.jpa = jpa;
        this.mapeador = mapeador;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(UUID id) {
        return jpa.findById(id).map(mapeador::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return jpa.findByCorreo(correo).map(mapeador::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarAdministradores() {
        return jpa.contarConRol(Rol.ADMINISTRADOR);
    }

    @Override
    @Transactional
    public void guardar(Usuario usuario) {
        UsuarioEntity entidad = jpa.findById(usuario.id()).orElseGet(mapeador::nuevaEntidad);
        mapeador.volcarEn(entidad, usuario);
        try {
            jpa.saveAndFlush(entidad);
        } catch (DataIntegrityViolationException e) {
            // Solo el constraint del correo es un error "de negocio"; cualquier otra
            // violación (columna nula, etc.) es un bug y debe seguir siendo un 500.
            String causa = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (causa.contains(CONSTRAINT_CORREO_UNICO)) {
                throw new CorreoYaRegistradoException(usuario.correo());
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return jpa.findAllByOrderByNombreAsc().stream().map(mapeador::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Usuario> buscar(String texto) {
        String escapado = texto.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jpa.buscarPorTexto("%" + escapado + "%").stream().map(mapeador::aDominio).toList();
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        jpa.deleteById(id);
    }
}
