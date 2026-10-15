package com.vivero.fitodiagnostico.dominio.excepcion;

import java.util.UUID;

/**
 * No existe la planta, o existe pero el solicitante no tiene permiso para
 * verla. Es UNA sola excepción para los dos casos a propósito (RN3): si
 * "no existe" y "no es tuya" fueran errores distintos, cualquiera podría
 * recorrer ids y descubrir cuáles plantas existen en el sistema. La capa web
 * la traduce a 404 con el mismo cuerpo en ambos casos.
 */
public class PlantaNoEncontradaException extends RuntimeException {

    private final UUID id;

    public PlantaNoEncontradaException(UUID id) {
        super("no existe una planta con id " + id);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
