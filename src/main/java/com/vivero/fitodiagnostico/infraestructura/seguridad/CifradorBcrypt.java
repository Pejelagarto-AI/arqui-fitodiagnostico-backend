package com.vivero.fitodiagnostico.infraestructura.seguridad;

import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.puerto.CifradorDeContrasena;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link CifradorDeContrasena} con BCrypt (RN10). Es el único
 * archivo del proyecto que nombra {@code org.springframework.security.crypto}:
 * cambiar de algoritmo (Argon2, scrypt) es una clase nueva aquí.
 *
 * <p>BCrypt incluye la sal dentro del propio hash y su costo es configurable
 * ({@code fitodiagnostico.seguridad.bcrypt-coste}, 10 por defecto; las pruebas
 * lo bajan a 4 para no pagar cientos de milisegundos por cada registro).
 */
@Component
public class CifradorBcrypt implements CifradorDeContrasena {

    private final BCryptPasswordEncoder codificador;

    public CifradorBcrypt(@Value("${fitodiagnostico.seguridad.bcrypt-coste:10}") int coste) {
        this.codificador = new BCryptPasswordEncoder(coste);
    }

    @Override
    public Credencial cifrar(String contrasena) {
        return new Credencial(codificador.encode(contrasena));
    }

    @Override
    public boolean coincide(String contrasena, Credencial credencial) {
        return contrasena != null && codificador.matches(contrasena, credencial.hash());
    }
}
