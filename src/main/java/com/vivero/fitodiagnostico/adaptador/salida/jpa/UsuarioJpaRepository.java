package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, UUID> {

    Optional<UsuarioEntity> findByCorreo(String correo);

    List<UsuarioEntity> findAllByOrderByNombreAsc();

    @Query("select count(u) from UsuarioEntity u where :rol member of u.roles")
    long contarConRol(@Param("rol") Rol rol);

    // El patrón ya viene con los comodines y los caracteres especiales escapados con '\'
    // (ver AdaptadorUsuarioJpa.buscar): un usuario que busque "50%" no debe listar a todos.
    @Query("select u from UsuarioEntity u "
         + "where lower(u.nombre) like :patron escape '\\' or u.correo like :patron escape '\\' "
         + "order by u.nombre")
    List<UsuarioEntity> buscarPorTexto(@Param("patron") String patron);
}
