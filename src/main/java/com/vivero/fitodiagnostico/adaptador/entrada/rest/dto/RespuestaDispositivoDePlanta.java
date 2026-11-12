package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.time.Instant;

/**
 * Un dispositivo de la planta, para el dueño. {@code ultimaCredencialEn} nulo significa "esperando que se
 * conecte"; con valor, el dispositivo ya pidió credencial (y sigue renovándola mientras esté encendido).
 */
public record RespuestaDispositivoDePlanta(String serial, Instant reclamadoEn, Instant ultimaCredencialEn) {

    public static RespuestaDispositivoDePlanta desde(Dispositivo d) {
        return new RespuestaDispositivoDePlanta(d.serial(), d.reclamadoEn().orElseThrow(),
                d.ultimaCredencialEn().orElse(null));
    }
}
