package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaDispositivoDePlanta;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaReclamoDeDispositivo;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudDispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.ReclamarDispositivoPort;
import com.vivero.fitodiagnostico.salud.ConsultarSaludPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de entrada de la PERSONA para los dispositivos de su planta: reclamar por serial, ver cuáles
 * tiene y cuándo se conectaron por última vez, y desvincular. El dueño nunca ve ni copia una credencial:
 * el dispositivo la pide solo (ver {@code AutenticacionDeDispositivoController}). Reclamar y desvincular son solo del
 * dueño (un invitado recibe 403). Desde la fase 12 (D173) el invitado puede LEER la lista, con la salud de cada matera,
 * igual que lee la planta; un ajeno recibe 404 en todo, como en los demás endpoints de la planta.
 */
@RestController
@RequestMapping("/api/v1/plantas/{plantaId}/dispositivos")
public class DispositivosDePlantaController {

    private final ReclamarDispositivoPort dispositivos;
    private final ConsultarSaludPort salud;

    public DispositivosDePlantaController(ReclamarDispositivoPort dispositivos, ConsultarSaludPort salud) {
        this.dispositivos = dispositivos;
        this.salud = salud;
    }

    /** 201 con el dispositivo reclamado. Reclamar otra vez el mismo serial para la misma planta es idempotente (también 201). */
    @PostMapping
    public ResponseEntity<RespuestaReclamoDeDispositivo> reclamar(Authentication autenticacion,
                                                                  @PathVariable UUID plantaId,
                                                                  @Valid @RequestBody SolicitudDispositivo solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaReclamoDeDispositivo.desde(
                dispositivos.reclamar(IdentidadDelSolicitante.id(autenticacion), plantaId, solicitud.serial())));
    }

    @GetMapping
    public List<RespuestaDispositivoDePlanta> listar(Authentication autenticacion, @PathVariable UUID plantaId) {
        List<Dispositivo> lista = dispositivos.listar(IdentidadDelSolicitante.id(autenticacion), plantaId);
        Map<String, ConsultarSaludPort.Vista> vistas = salud.deLosDispositivos(
                lista.stream().map(Dispositivo::serial).toList());
        return lista.stream().map(d -> RespuestaDispositivoDePlanta.desde(d, vistas.get(d.serial()))).toList();
    }

    @DeleteMapping("/{serial}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(Authentication autenticacion, @PathVariable UUID plantaId, @PathVariable String serial) {
        dispositivos.desvincular(IdentidadDelSolicitante.id(autenticacion), plantaId, serial);
    }
}
