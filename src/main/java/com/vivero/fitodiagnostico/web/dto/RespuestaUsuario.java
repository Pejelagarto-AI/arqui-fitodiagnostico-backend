package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

import java.util.List;
import java.util.UUID;

/**
 * Forma pública de un usuario. Nunca incluye la credencial, ni siquiera el
 * hash (RN10): este DTO es el único camino de un {@code Usuario} hacia afuera.
 */
public record RespuestaUsuario(UUID id, String nombre, String correo, List<String> roles) {

    public static RespuestaUsuario desde(Usuario usuario) {
        return new RespuestaUsuario(usuario.id(), usuario.nombre(), usuario.correo(),
                usuario.roles().stream().sorted().map(Enum::name).toList());
    }
}
