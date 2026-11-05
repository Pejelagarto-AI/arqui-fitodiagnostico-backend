package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

/**
 * Puerto de entrada del DISPOSITIVO que se da de alta solo la primera vez que prende, firmando con la clave
 * de la flota. Regla de negocio: solo se autorregistra lo que firma la flota.
 */
public interface AutorregistrarDispositivoPort {

    /** {@code creado} es true si el dispositivo era nuevo y false si ya existía con el mismo secreto (idempotente). */
    record Resultado(Dispositivo dispositivo, boolean creado) { }

    /**
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.FirmaDeFlotaInvalidaException si la firma no coincide
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaExisteException si el serial existe con otro secreto
     * @throws IllegalArgumentException si el serial no cumple el formato o el secreto es demasiado corto
     */
    default Resultado registrar(String serial, String secreto, String firma) {
        return registrar(serial, secreto, firma, null);
    }

    /** Igual, anotando desde qué dirección habló el dispositivo (null si no se conoce). */
    Resultado registrar(String serial, String secreto, String firma, String ip);
}
