package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

/** Puerto de entrada: iniciar sesión con correo y contraseña (RF-A2). */
public interface IniciarSesionPort {

    /** Resultado de iniciar sesión: quién es y con qué prueba entra. */
    record Sesion(Usuario usuario, TokenDeSesion token) { }

    Sesion iniciarSesion(String correo, String contrasena);
}
