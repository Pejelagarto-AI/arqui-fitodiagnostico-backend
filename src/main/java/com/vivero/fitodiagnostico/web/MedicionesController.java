package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioMediciones;
import com.vivero.fitodiagnostico.web.dto.RespuestaDiagnosticoDePlanta;
import com.vivero.fitodiagnostico.web.dto.SolicitudMedicion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada HUMANO del caso de uso de registrar una medición
 * (RF-F1, origen MANUAL). El de la maceta será otro adaptador de entrada que
 * llame al mismo {@code ServicioMediciones}. Solo el dueño (RN5): un invitado
 * recibe 403 y un ajeno 404.
 */
@RestController
@RequestMapping("/api/v1/plantas/{plantaId}/mediciones")
public class MedicionesController {

    private final ServicioMediciones servicio;

    public MedicionesController(ServicioMediciones servicio) {
        this.servicio = servicio;
    }

    /** Responde 201 con el diagnóstico que quedó en el historial. */
    @PostMapping
    public ResponseEntity<RespuestaDiagnosticoDePlanta> registrar(Authentication autenticacion,
                                                                  @PathVariable UUID plantaId,
                                                                  @Valid @RequestBody SolicitudMedicion solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaDiagnosticoDePlanta.desde(
                servicio.registrarManual(IdentidadDelSolicitante.id(autenticacion), plantaId, solicitud.aMedicion())));
    }
}
