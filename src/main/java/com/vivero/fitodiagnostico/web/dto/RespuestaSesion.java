package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;

import java.time.Instant;

/**
 * Respuesta de iniciar sesión (RF-A2). {@code roles} va en {@code usuario} solo
 * para que el front decida qué menús mostrar; la autorización real la decide el
 * servidor con la base, nunca con lo que el front diga que es.
 */
public record RespuestaSesion(String token, String tipo, Instant expiraEn, RespuestaUsuario usuario) {

    public static RespuestaSesion desde(ServicioCuentas.Sesion sesion) {
        return new RespuestaSesion(sesion.token().valor(), "Bearer", sesion.token().expiraEn(),
                RespuestaUsuario.desde(sesion.usuario()));
    }
}
