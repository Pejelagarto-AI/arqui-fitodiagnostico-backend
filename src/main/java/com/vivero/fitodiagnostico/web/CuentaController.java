package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import com.vivero.fitodiagnostico.web.dto.RespuestaUsuario;
import com.vivero.fitodiagnostico.web.dto.SolicitudActualizarDatos;
import com.vivero.fitodiagnostico.web.dto.SolicitudCambioContrasena;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cuenta del propio solicitante (RF-A4 a RF-A6). Ninguna ruta lleva un id:
 * "mi cuenta" es siempre la del token, así que no existe forma de pedir la
 * de otro por error de URL (RN3 aplicada al módulo de cuentas).
 */
@RestController
@RequestMapping("/api/v1/cuenta")
public class CuentaController {

    private final ServicioCuentas servicio;

    public CuentaController(ServicioCuentas servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public RespuestaUsuario consultar(Authentication autenticacion) {
        return RespuestaUsuario.desde(servicio.consultar(IdentidadDelSolicitante.id(autenticacion)));
    }

    @PutMapping
    public RespuestaUsuario actualizar(Authentication autenticacion,
                                       @Valid @RequestBody SolicitudActualizarDatos solicitud) {
        return RespuestaUsuario.desde(servicio.actualizarDatos(
                IdentidadDelSolicitante.id(autenticacion), solicitud.nombre(), solicitud.correo()));
    }

    @PutMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarContrasena(Authentication autenticacion,
                                  @Valid @RequestBody SolicitudCambioContrasena solicitud) {
        servicio.cambiarContrasena(IdentidadDelSolicitante.id(autenticacion),
                solicitud.contrasenaActual(), solicitud.contrasenaNueva());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(Authentication autenticacion) {
        servicio.eliminar(IdentidadDelSolicitante.id(autenticacion));
    }
}
