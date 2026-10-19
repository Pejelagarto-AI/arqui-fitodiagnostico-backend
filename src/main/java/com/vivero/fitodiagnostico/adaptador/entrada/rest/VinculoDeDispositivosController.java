package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaTokenDeDispositivo;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudDispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VincularDispositivoPort;
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
 * Adaptador de entrada de la PERSONA para autorizar un dispositivo: el dueño (con su JWT de usuario)
 * recibe el token que copiará al ESP32. Solo el dueño: un invitado recibe 403 y un ajeno 404, igual que
 * en los demás endpoints de la planta. No emite nada por sí mismo: llama al puerto de entrada.
 */
@RestController
@RequestMapping("/api/v1/plantas/{plantaId}/dispositivos")
public class VinculoDeDispositivosController {

    private final VincularDispositivoPort dispositivos;

    public VinculoDeDispositivosController(VincularDispositivoPort dispositivos) {
        this.dispositivos = dispositivos;
    }

    /** Responde 201 con el token del dispositivo; el token no se vuelve a poder consultar. */
    @PostMapping
    public ResponseEntity<RespuestaTokenDeDispositivo> vincular(Authentication autenticacion,
                                                                @PathVariable UUID plantaId,
                                                                @Valid @RequestBody SolicitudDispositivo solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaTokenDeDispositivo.desde(
                dispositivos.vincular(IdentidadDelSolicitante.id(autenticacion), plantaId, solicitud.dispositivoId())));
    }
}
