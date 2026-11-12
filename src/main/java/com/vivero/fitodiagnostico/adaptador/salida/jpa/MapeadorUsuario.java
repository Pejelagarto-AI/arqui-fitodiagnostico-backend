package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.springframework.stereotype.Component;

/** Convierte entre {@link UsuarioEntity} y {@link Usuario}; ninguna entidad sale de este paquete. */
@Component
public class MapeadorUsuario {

    public Usuario aDominio(UsuarioEntity entidad) {
        return new Usuario(entidad.getId(), entidad.getNombre(), entidad.getCorreo(),
                new Credencial(entidad.getHashContrasena()), entidad.getRoles());
    }

    UsuarioEntity nuevaEntidad() {
        return new UsuarioEntity();
    }

    void volcarEn(UsuarioEntity entidad, Usuario usuario) {
        entidad.setId(usuario.id());
        entidad.setNombre(usuario.nombre());
        entidad.setCorreo(usuario.correo());
        entidad.setHashContrasena(usuario.credencial().hash());
        // Se actualiza el mismo Set (no se reemplaza) para que Hibernate calcule
        // el delta de filas en usuario_rol en vez de borrar y reinsertar todo.
        entidad.getRoles().retainAll(usuario.roles());
        entidad.getRoles().addAll(usuario.roles());
    }
}
