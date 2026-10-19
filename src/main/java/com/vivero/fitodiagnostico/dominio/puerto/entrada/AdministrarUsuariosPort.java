package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

import java.util.List;
import java.util.UUID;

/** Puerto de entrada del administrador sobre los usuarios (RF-B1 a RF-B5). */
public interface AdministrarUsuariosPort {

    /** Un usuario con sus plantas (RF-B2). */
    record DetalleDeUsuario(Usuario usuario, List<Planta> plantas) { }

    List<Usuario> listarUsuarios(String texto);

    DetalleDeUsuario detalleDeUsuario(UUID id);

    Usuario editarUsuario(UUID id, String nombre, String correo);

    void eliminarUsuario(UUID id);

    Usuario otorgarAdministrador(UUID id);

    Usuario revocarAdministrador(UUID id);
}
