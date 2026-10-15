package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.aplicacion.ServicioInvitaciones;

/**
 * El visitante que se registró y aceptó: su cuenta nueva y la planta a la que ya
 * tiene acceso. No trae sesión: iniciar sesión es un paso aparte (D30), igual que en el registro normal.
 */
public record RespuestaAceptacionConRegistro(RespuestaUsuario usuario, RespuestaPlanta planta) {

    public static RespuestaAceptacionConRegistro desde(ServicioInvitaciones.AceptacionConRegistro aceptacion) {
        return new RespuestaAceptacionConRegistro(RespuestaUsuario.desde(aceptacion.usuario()),
                RespuestaPlanta.desde(aceptacion.planta(), aceptacion.usuario().id()));
    }
}
