package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.AutenticarDispositivoPort;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada del DISPOSITIVO para conseguir su credencial: pide un reto y lo contesta. Son rutas
 * PÚBLICAS (el chip todavía no tiene token); lo que protege es el secreto, no la ruta. No hay lógica aquí:
 * traduce HTTP a {@link AutenticarDispositivoPort} y los errores salen por el manejador global
 * (404 DISPOSITIVO_NO_ENCONTRADO, 409 DISPOSITIVO_SIN_RECLAMAR, 401 CREDENCIAL_RECHAZADA).
 *
 * <p>Vive junto a {@link LecturasDeDispositivoController} y repite sus propios DTO (D73).
 */
@RestController
@RequestMapping("/api/v1/dispositivos/{serial}")
public class AutenticacionDeDispositivoController {

    private final AutenticarDispositivoPort autenticacion;

    public AutenticacionDeDispositivoController(AutenticarDispositivoPort autenticacion) {
        this.autenticacion = autenticacion;
    }

    /** 200 con un reto nuevo (reemplaza al anterior). Sin cuerpo. */
    @PostMapping("/reto")
    public RespuestaReto reto(@PathVariable String serial) {
        return RespuestaReto.desde(autenticacion.reto(serial));
    }

    /** 200 con la credencial. El reto se consume aunque falle. */
    @PostMapping("/credencial")
    public RespuestaCredencial credencial(@PathVariable String serial, @Valid @RequestBody SolicitudCredencial solicitud) {
        return RespuestaCredencial.desde(autenticacion.credencial(serial, solicitud.respuesta()));
    }
}
