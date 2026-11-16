package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.salud.ConsultarSaludPort;

import java.time.Instant;

/**
 * Un dispositivo de la planta, para el dueño. {@code ultimaCredencialEn} nulo significa "esperando que se
 * conecte"; con valor, el dispositivo ya pidió credencial (y sigue renovándola mientras esté encendido).
 * Fase 10: {@code ultimaLecturaEn} y {@code salud} son nulos mientras la matera nunca haya dejado noticia.
 */
public record RespuestaDispositivoDePlanta(String serial, Instant reclamadoEn, Instant ultimaCredencialEn,
                                           Instant ultimaLecturaEn, RespuestaSalud salud) {

    public static RespuestaDispositivoDePlanta desde(Dispositivo d, ConsultarSaludPort.Vista vista) {
        return new RespuestaDispositivoDePlanta(d.serial(), d.reclamadoEn().orElseThrow(),
                d.ultimaCredencialEn().orElse(null),
                vista == null ? null : vista.salud().ultimaLecturaEn(), RespuestaSalud.desde(vista));
    }
}
