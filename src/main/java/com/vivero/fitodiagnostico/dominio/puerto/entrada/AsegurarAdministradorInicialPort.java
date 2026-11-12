package com.vivero.fitodiagnostico.dominio.puerto.entrada;

/** Puerto de entrada del arranque: crear el primer administrador si todavía no hay ninguno. */
public interface AsegurarAdministradorInicialPort {

    /** @return true si lo creó, false si ya existía alguno o faltan credenciales */
    boolean asegurarAdministradorInicial(String correo, String contrasena);
}
