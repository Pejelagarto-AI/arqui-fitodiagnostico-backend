package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarCuentaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.IniciarSesionPort;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaSesion;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaUsuario;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudInicioSesion;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudRegistro;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las dos rutas públicas del módulo A: registrarse (RF-A1) e iniciar sesión
 * (RF-A2). RF-A3, cerrar sesión, no tiene ruta: el token no se guarda en el
 * servidor, así que "cerrar" es que el front lo descarte.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final RegistrarCuentaPort registro;
    private final IniciarSesionPort sesiones;

    public AutenticacionController(RegistrarCuentaPort registro, IniciarSesionPort sesiones) {
        this.registro = registro;
        this.sesiones = sesiones;
    }

    @PostMapping("/registro")
    public ResponseEntity<RespuestaUsuario> registrar(@Valid @RequestBody SolicitudRegistro solicitud) {
        Usuario usuario = registro.registrar(solicitud.nombre(), solicitud.correo(), solicitud.contrasena());
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaUsuario.desde(usuario));
    }

    @PostMapping("/sesion")
    public RespuestaSesion iniciarSesion(@Valid @RequestBody SolicitudInicioSesion solicitud) {
        return RespuestaSesion.desde(sesiones.iniciarSesion(solicitud.correo(), solicitud.contrasena()));
    }
}
