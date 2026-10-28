package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CifradorDeContrasena;

/**
 * Doble determinista de {@link CifradorDeContrasena}: "cifra" anteponiendo un
 * prefijo. No protege nada y no lo pretende; permite probar los servicios sin
 * BCrypt (y sin su costo de tiempo) y comprobar que nunca se guarda la
 * contraseña en claro.
 */
public class CifradorFalso implements CifradorDeContrasena {

    public static final String PREFIJO = "hash:";

    @Override
    public Credencial cifrar(String contrasena) {
        return new Credencial(PREFIJO + contrasena);
    }

    @Override
    public boolean coincide(String contrasena, Credencial credencial) {
        return credencial.hash().equals(PREFIJO + contrasena);
    }
}
