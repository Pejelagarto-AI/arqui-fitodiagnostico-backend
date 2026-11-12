package com.vivero.fitodiagnostico.dominio.puerto.entrada;

/** Puerto de entrada: recuperar el acceso a una cuenta por correo (RF-A7). */
public interface RecuperarContrasenaPort {

    /** Responde igual exista o no el correo, para no revelar qué cuentas hay. */
    void solicitar(String correo);

    void restablecer(String token, String contrasenaNueva);
}
