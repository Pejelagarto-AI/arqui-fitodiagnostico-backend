package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioSolicitudes;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import com.vivero.fitodiagnostico.web.dto.RespuestaSolicitudDeEspecie;
import com.vivero.fitodiagnostico.web.dto.SolicitudDeEspecieNueva;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Del cliente: pedir una especie nueva (RF-H1) y ver el estado de sus solicitudes (RF-H2). */
@RestController
@RequestMapping("/api/v1/solicitudes-especie")
public class SolicitudesEspecieController {

    private final ServicioSolicitudes servicio;

    public SolicitudesEspecieController(ServicioSolicitudes servicio) {
        this.servicio = servicio;
    }

    /** RF-H1. 409 si el catálogo ya tiene una especie con ese nombre. */
    @PostMapping
    public ResponseEntity<RespuestaSolicitudDeEspecie> enviar(Authentication autenticacion,
                                                              @Valid @RequestBody SolicitudDeEspecieNueva solicitud) {
        SolicitudEspecie creada = servicio.enviar(IdentidadDelSolicitante.id(autenticacion),
                solicitud.nombre(), solicitud.justificacion());
        return ResponseEntity.created(URI.create("/api/v1/solicitudes-especie"))
                .body(RespuestaSolicitudDeEspecie.desde(creada));
    }

    /** RF-H2: solo las propias. */
    @GetMapping
    public List<RespuestaSolicitudDeEspecie> propias(Authentication autenticacion) {
        return servicio.deSolicitante(IdentidadDelSolicitante.id(autenticacion)).stream()
                .map(RespuestaSolicitudDeEspecie::desde).toList();
    }
}
