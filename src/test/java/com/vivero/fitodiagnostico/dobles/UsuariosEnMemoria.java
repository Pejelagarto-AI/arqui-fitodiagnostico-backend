package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioUsuarios;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria de {@link RepositorioUsuarios}. Imita lo que garantiza el
 * adaptador real: el correo es único (RN1) y {@code guardar} lo hace cumplir
 * lanzando {@link CorreoYaRegistradoException}.
 */
public class UsuariosEnMemoria implements RepositorioUsuarios {

    private final Map<UUID, Usuario> porId = new LinkedHashMap<>();

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return porId.values().stream().filter(u -> u.correo().equals(correo)).findFirst();
    }

    @Override
    public long contarAdministradores() {
        return porId.values().stream().filter(Usuario::esAdministrador).count();
    }

    @Override
    public void guardar(Usuario usuario) {
        boolean correoOcupado = porId.values().stream()
                .anyMatch(u -> u.correo().equals(usuario.correo()) && !u.id().equals(usuario.id()));
        if (correoOcupado) {
            throw new CorreoYaRegistradoException(usuario.correo());
        }
        porId.put(usuario.id(), usuario);
    }

    @Override
    public List<Usuario> listar() {
        return porId.values().stream().sorted(Comparator.comparing(Usuario::nombre)).toList();
    }

    @Override
    public List<Usuario> buscar(String texto) {
        String aguja = texto.toLowerCase(Locale.ROOT);
        return listar().stream()
                .filter(u -> u.nombre().toLowerCase(Locale.ROOT).contains(aguja) || u.correo().contains(aguja))
                .toList();
    }

    @Override
    public void eliminar(UUID id) {
        porId.remove(id);
    }
}
