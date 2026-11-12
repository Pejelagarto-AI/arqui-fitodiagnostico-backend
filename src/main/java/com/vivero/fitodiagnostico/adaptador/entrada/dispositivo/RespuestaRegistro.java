package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.time.Instant;

/** El dispositivo registrado: nunca incluye el secreto, que el chip ya conoce. */
public record RespuestaRegistro(String serial, String estado, Instant creadoEn) {

    static RespuestaRegistro desde(Dispositivo d) {
        return new RespuestaRegistro(d.serial(), d.estaReclamado() ? "RECLAMADO" : "SIN_RECLAMAR", d.creadoEn());
    }
}
