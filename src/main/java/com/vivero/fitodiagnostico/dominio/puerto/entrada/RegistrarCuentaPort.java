package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

/** Puerto de entrada: crear una cuenta (RF-A1). */
public interface RegistrarCuentaPort {

    Usuario registrar(String nombre, String correo, String contrasena);
}
