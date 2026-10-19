package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.RecuperarContrasenaPort;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudNuevaContrasena;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudRecuperacion;
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

    private final RecuperarContrasenaPort recuperacion;

    public RecuperacionController(RecuperarContrasenaPort recuperacion) {
        this.recuperacion = recuperacion;
    }

    /**
     * RF-A7. Responde 202 con el MISMO cuerpo exista o no el correo (D52): el
     * texto dice "si existe", sin confirmar nada.
     */
    @PostMapping("/recuperacion")
    public ResponseEntity<Map<String, String>> solicitar(@Valid @RequestBody SolicitudRecuperacion solicitud) {
        recuperacion.solicitar(solicitud.correo());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("mensaje",
                "Si el correo está registrado, te enviamos un enlace para restablecer la contraseña."));
    }

    /** RF-A8. 410 si el enlace ya se usó, caducó o la contraseña cambió. */
    @PostMapping("/restablecimiento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody SolicitudNuevaContrasena solicitud) {
        recuperacion.restablecer(solicitud.token(), solicitud.contrasenaNueva());
    }
}
