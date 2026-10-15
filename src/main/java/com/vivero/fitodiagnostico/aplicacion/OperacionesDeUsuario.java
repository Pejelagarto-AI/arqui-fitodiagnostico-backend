package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioUsuarios;

import java.util.UUID;

/**
 * Pasos que comparten {@link ServicioCuentas} y {@link ServicioAdministracion}:
 * editar los datos de un usuario es la misma operación sea el dueño de la
 * cuenta (RF-A4) o un administrador (RF-B3); solo cambia quién la autoriza,
 * y eso lo decide el borde web. Sin estado y sin Spring: funciones sobre el
 * puerto, visibles solo dentro del paquete.
 */
final class OperacionesDeUsuario {

    private OperacionesDeUsuario() { }

    static Usuario cargar(RepositorioUsuarios usuarios, UUID id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }

    /** RN1: falla si otro usuario (distinto de {@code propietario}) ya usa ese correo normalizado. */
    static void exigirCorreoLibre(RepositorioUsuarios usuarios, String correoNormalizado, UUID propietario) {
        usuarios.buscarPorCorreo(correoNormalizado)
                .filter(existente -> !existente.id().equals(propietario))
                .ifPresent(existente -> {
                    throw new CorreoYaRegistradoException(correoNormalizado);
                });
    }

    static Usuario editarDatos(RepositorioUsuarios usuarios, UUID id, String nombre, String correo) {
        Usuario actual = cargar(usuarios, id);
        Usuario editado = actual.conDatos(nombre, correo);
        exigirCorreoLibre(usuarios, editado.correo(), id);
        usuarios.guardar(editado);
        return editado;
    }
}
