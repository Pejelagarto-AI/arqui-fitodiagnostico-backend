package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarLecturaPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada del DISPOSITIVO (la matera con su ESP32, o su simulador): traduce un POST con una
 * lectura a una llamada al puerto {@link RegistrarLecturaPort} con origen {@code DISPOSITIVO}. Es lo que
 * hace falta para agregar el canal nuevo: esta carpeta, y nada del dominio.
 *
 * <p>Qué hace, y solo esto:
 * <ol>
 *   <li>Comprueba el token que la cadena de seguridad ya validó (firma, caducidad, emisor): que su
 *       alcance sea {@code lecturas:reportar} y que su {@code sub} sea el dispositivo de la ruta.
 *       Un dispositivo no puede reportar por otro.</li>
 *   <li>Toma la planta del TOKEN, no del cuerpo ni de la ruta: el dueño decidió a qué planta sirve
 *       cada dispositivo al emitirle el token, y el dispositivo no puede cambiarlo.</li>
 *   <li>Convierte el cuerpo a objetos de dominio ({@link SolicitudLectura#aMedicion()}); lo que sea
 *       inválido sale por el manejador global de errores con el cuerpo uniforme.</li>
 * </ol>
 * El dominio no ve cabeceras, ni JWT, ni JSON: recibe la planta, la medición, el origen y el instante.
 */
@RestController
@RequestMapping("/api/v1/dispositivos/{dispositivoId}/lecturas")
public class LecturasDeDispositivoController {

    // El contrato del token lo escribe EmisorTokenDispositivoJwt (otro adaptador); aquí se lee, sin depender de él.
    private static final String CLAIM_PLANTA = "planta";
    private static final String CLAIM_ALCANCE = "scope";
    private static final String ALCANCE_REPORTAR_LECTURAS = "lecturas:reportar";

    private final RegistrarLecturaPort lecturas;

    public LecturasDeDispositivoController(RegistrarLecturaPort lecturas) {
        this.lecturas = lecturas;
    }

    /** 201 con el diagnóstico guardado. 400 si el cuerpo es inválido; 403 si el token no es de este dispositivo. */
    @PostMapping
    public ResponseEntity<RespuestaLectura> reportar(@AuthenticationPrincipal Jwt token,
                                                     @PathVariable String dispositivoId,
                                                     @Valid @RequestBody SolicitudLectura solicitud) {
        if (!ALCANCE_REPORTAR_LECTURAS.equals(token.getClaimAsString(CLAIM_ALCANCE))) {
            throw new OperacionNoPermitidaException("el token no permite reportar lecturas");
        }
        if (!dispositivoId.equals(token.getSubject())) {
            throw new OperacionNoPermitidaException("el token pertenece a otro dispositivo");
        }
        UUID plantaId = UUID.fromString(token.getClaimAsString(CLAIM_PLANTA));

        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaLectura.desde(
                lecturas.registrar(plantaId, solicitud.aMedicion(), OrigenDeMedicion.DISPOSITIVO,
                        solicitud.marcaDeTiempo())));
    }
}
