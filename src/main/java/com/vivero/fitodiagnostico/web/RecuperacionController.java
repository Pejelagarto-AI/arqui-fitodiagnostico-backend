package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioRecuperacion;
import com.vivero.fitodiagnostico.web.dto.SolicitudNuevaContrasena;
import com.vivero.fitodiagnostico.web.dto.SolicitudRecuperacion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Recuperación de contraseña (RF-A7, RF-A8). Las dos rutas son públicas: quien
 * olvidó su contraseña no tiene sesión.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class RecuperacionController {

    private final ServicioRecuperacion servicio;

    public RecuperacionController(ServicioRecuperacion servicio) {
        this.servicio = servicio;
    }

    /**
     * RF-A7. Responde 202 con el MISMO cuerpo exista o no el correo (D52): el
     * texto dice "si existe", sin confirmar nada.
     */
    @PostMapping("/recuperacion")
    public ResponseEntity<Map<String, String>> solicitar(@Valid @RequestBody SolicitudRecuperacion solicitud) {
        servicio.solicitar(solicitud.correo());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("mensaje",
                "Si el correo está registrado, te enviamos un enlace para restablecer la contraseña."));
    }

    /** RF-A8. 410 si el enlace ya se usó, caducó o la contraseña cambió. */
    @PostMapping("/restablecimiento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody SolicitudNuevaContrasena solicitud) {
        servicio.restablecer(solicitud.token(), solicitud.contrasenaNueva());
    }
}
