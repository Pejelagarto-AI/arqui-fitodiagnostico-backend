package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

import java.util.UUID;

/** Puerto de entrada: lo que una persona hace con su propia cuenta (RF-A4 a RF-A6). */
public interface GestionarCuentaPort {

    Usuario actualizarDatos(UUID id, String nombre, String correo);

    void cambiarContrasena(UUID id, String contrasenaActual, String contrasenaNueva);

    void eliminar(UUID id);
}
