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
    Reto reto(String serial);

    /**
     * Gasta el reto vigente: lo consume aunque falle.
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoNoEncontradoException si el serial no existe
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoSinReclamarException si nadie lo reclamó
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.CredencialRechazadaException si no hay reto vigente
     *         o la respuesta no corresponde
     */
    TokenDeDispositivo credencial(String serial, String respuesta);
}
