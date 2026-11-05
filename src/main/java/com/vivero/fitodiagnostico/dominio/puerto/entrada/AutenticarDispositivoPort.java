package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Reto;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;

/**
 * Puerto de entrada del DISPOSITIVO: pide un reto y lo contesta para obtener su credencial. No hay
 * usuario: la identidad es el serial y la prueba es conocer el secreto.
 */
public interface AutenticarDispositivoPort {

    /**
     * Un reto nuevo (reemplaza al anterior).
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoNoEncontradoException si el serial no existe
     */
    default Reto reto(String serial) {
        return reto(serial, null);
    }

    /** Igual, anotando el contacto y la dirección desde la que habló el dispositivo (null si no se conoce). */
    Reto reto(String serial, String ip);

    /**
     * Gasta el reto vigente: lo consume aunque falle.
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoNoEncontradoException si el serial no existe
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoSinReclamarException si nadie lo reclamó
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.CredencialRechazadaException si no hay reto vigente
     *         o la respuesta no corresponde
     */
    default TokenDeDispositivo credencial(String serial, String respuesta) {
        return credencial(serial, respuesta, null).token();
    }

    /** La credencial y el apodo de la planta a la que sirve (la pantalla de la matera muestra el nombre, no el código). */
    record CredencialEmitida(TokenDeDispositivo token, String apodo) { }

    /** Igual, anotando el contacto y la dirección desde la que habló el dispositivo (null si no se conoce). */
    CredencialEmitida credencial(String serial, String respuesta, String ip);
}
