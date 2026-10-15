package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.Credencial;

/**
 * Puerto que esconde el algoritmo de hash (RN10, RA4). El dominio y la
 * aplicación piden "cíframe esto" y "¿coincide?"; que detrás haya BCrypt, y de
 * qué librería, es un detalle de infraestructura que se puede cambiar sin
 * tocar ninguna regla.
 */
public interface CifradorDeContrasena {

    Credencial cifrar(String contrasena);

    boolean coincide(String contrasena, Credencial credencial);
}
