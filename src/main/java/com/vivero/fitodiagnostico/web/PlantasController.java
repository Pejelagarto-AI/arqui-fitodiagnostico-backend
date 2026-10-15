package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioPlantas;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.web.dto.RespuestaPlanta;
import com.vivero.fitodiagnostico.web.dto.SolicitudPlanta;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Las plantas del solicitante (RF-D1 a RF-D5). "Quién soy" sale siempre del
 * token. Una planta ajena a la que no se tiene acceso responde 404 igual que
 * una inexistente (RN3, D35). Cualquier cliente autenticado puede llamar: ser
 * administrador no da acceso a plantas ajenas.
 */
@RestController
@RequestMapping("/api/v1/plantas")
public class PlantasController {

    private final ServicioPlantas servicio;

    public PlantasController(ServicioPlantas servicio) {
        this.servicio = servicio;
    }

    /** RF-D1. 404 si la especie no existe; 409 si está descontinuada (RN11). */
    @PostMapping
    public ResponseEntity<RespuestaPlanta> registrar(Authentication autenticacion,
                                                     @Valid @RequestBody SolicitudPlanta solicitud) {
        UUID solicitante = IdentidadDelSolicitante.id(autenticacion);
        Planta planta = servicio.registrar(solicitante, solicitud.apodo(), solicitud.especie());
        return ResponseEntity.created(URI.create("/api/v1/plantas/" + planta.id()))
                .body(RespuestaPlanta.desde(planta, solicitante));
    }

    /** RF-D2: solo las propias. */
    @GetMapping
    public List<RespuestaPlanta> listar(Authentication autenticacion) {
        UUID solicitante = IdentidadDelSolicitante.id(autenticacion);
        return servicio.listarPropias(solicitante).stream()
                .map(planta -> RespuestaPlanta.desde(planta, solicitante)).toList();
    }

    /** RF-D3: dueño o invitado. */
    @GetMapping("/{id}")
    public RespuestaPlanta detalle(Authentication autenticacion, @PathVariable UUID id) {
        UUID solicitante = IdentidadDelSolicitante.id(autenticacion);
        return RespuestaPlanta.desde(servicio.detalle(solicitante, id), solicitante);
    }

    /** RF-D4: solo el dueño (403 al invitado). */
    @PutMapping("/{id}")
    public RespuestaPlanta editar(Authentication autenticacion, @PathVariable UUID id,
                                  @Valid @RequestBody SolicitudPlanta solicitud) {
        UUID solicitante = IdentidadDelSolicitante.id(autenticacion);
        return RespuestaPlanta.desde(
                servicio.editar(solicitante, id, solicitud.apodo(), solicitud.especie()), solicitante);
    }

    /** RF-D5: solo el dueño; borra historial y accesos y avisa a los invitados (RN12). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(Authentication autenticacion, @PathVariable UUID id) {
        servicio.eliminar(IdentidadDelSolicitante.id(autenticacion), id);
    }
}
