package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de usuarios. Los correos que recibe ya vienen
 * normalizados ({@link Usuario#normalizarCorreo}); el adaptador no decide
 * qué es "el mismo correo".
 */
public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorId(UUID id);

    Optional<Usuario> buscarPorCorreo(String correo);

    /** Cuántos usuarios tienen el rol ADMINISTRADOR; lo consulta la RN4 (D18). */
    long contarAdministradores();

    /**
     * Crea o reemplaza (por id).
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException
     *         si otro usuario ya tiene ese correo; es la red de seguridad de la RN1
     *         cuando dos registros simultáneos pasan la comprobación previa
     */
    void guardar(Usuario usuario);

    /** Todos, ordenados por nombre (RF-B1). */
    List<Usuario> listar();

    /** Los que contienen el texto en nombre o correo, sin distinguir mayúsculas (RF-B1). */
    List<Usuario> buscar(String texto);

    void eliminar(UUID id);
}
