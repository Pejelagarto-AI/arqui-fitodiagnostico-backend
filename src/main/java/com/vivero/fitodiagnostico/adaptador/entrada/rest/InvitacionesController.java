package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.aplicacion.RutasDelFront;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.AceptarInvitacionPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.CompartirPlantaPort;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaAceptacionConRegistro;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaInvitacion;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaInvitacionGenerada;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaInvitado;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaPlanta;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudAceptacion;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudAceptacionConRegistro;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudInvitacion;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compartir plantas (RF-E1 a RF-E6). Tres grupos de rutas:
 * <ul>
 *   <li>del dueño, colgadas de la planta: invitaciones e invitados (403 al invitado, 404 al ajeno);</li>
 *   <li>del invitado: las compartidas conmigo y renunciar;</li>
 *   <li>de quien recibe el enlace: aceptar con sesión, y aceptar registrándose (esta última
 *       es PÚBLICA: el visitante todavía no tiene cuenta, RF-E2).</li>
 * </ul>
 * Las rutas {@code /plantas/compartidas} son literales y por eso tienen prioridad
 * sobre {@code /plantas/{id}} de {@link PlantasController}.
 */
@RestController
@RequestMapping("/api/v1")
public class InvitacionesController {

    private final CompartirPlantaPort compartir;
    private final AceptarInvitacionPort aceptacion;
    private final String urlDelFront;
    private final Clock reloj;

    /**
     * La URL base del front es configuración ({@code FITO_FRONT_URL}); las rutas de las pantallas
     * salen de {@link RutasDelFront}. Así el adaptador REST no depende de otros adaptadores.
     */
    public InvitacionesController(CompartirPlantaPort compartir, AceptarInvitacionPort aceptacion,
                                  @Value("${fitodiagnostico.front.url:http://localhost:5500}") String urlDelFront,
                                  Clock reloj) {
        this.compartir = compartir;
        this.aceptacion = aceptacion;
        this.urlDelFront = urlDelFront;
        this.reloj = reloj;
    }

    // ---- del dueño ----

    /** RF-E1: devuelve el token y el enlace UNA vez; con {@code correo} además lo envía. */
    @PostMapping("/plantas/{plantaId}/invitaciones")
    public ResponseEntity<RespuestaInvitacionGenerada> generar(Authentication autenticacion,
                                                               @PathVariable UUID plantaId,
                                                               @Valid @RequestBody(required = false) SolicitudInvitacion solicitud) {
        String correo = solicitud == null ? null : solicitud.correo();
        CompartirPlantaPort.InvitacionGenerada generada =
                compartir.generar(IdentidadDelSolicitante.id(autenticacion), plantaId, correo);
        return ResponseEntity
                .created(URI.create("/api/v1/plantas/" + plantaId + "/invitaciones"))
                .body(RespuestaInvitacionGenerada.desde(generada.invitacion(), generada.token(),
                        RutasDelFront.absoluta(urlDelFront, RutasDelFront.invitacion(generada.token()))));
    }

    /** Las invitaciones vigentes de la planta, sin token. */
    @GetMapping("/plantas/{plantaId}/invitaciones")
    public List<RespuestaInvitacion> pendientes(Authentication autenticacion, @PathVariable UUID plantaId) {
        Instant ahora = reloj.instant();
        return compartir.pendientes(IdentidadDelSolicitante.id(autenticacion), plantaId).stream()
                .map(invitacion -> RespuestaInvitacion.desde(invitacion, ahora)).toList();
    }

    /** RF-E3. */
    @DeleteMapping("/plantas/{plantaId}/invitaciones/{invitacionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarInvitacion(Authentication autenticacion, @PathVariable UUID plantaId,
                                  @PathVariable UUID invitacionId) {
        compartir.revocar(IdentidadDelSolicitante.id(autenticacion), plantaId, invitacionId);
    }

    /** Quiénes tienen acceso a la planta. */
    @GetMapping("/plantas/{plantaId}/invitados")
    public List<RespuestaInvitado> invitados(Authentication autenticacion, @PathVariable UUID plantaId) {
        return compartir.invitados(IdentidadDelSolicitante.id(autenticacion), plantaId).stream()
                .map(RespuestaInvitado::desde).toList();
    }

    /** RF-E4: el invitado recibe ACCESO_PERDIDO (RF-G3). */
    @DeleteMapping("/plantas/{plantaId}/invitados/{invitadoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarAcceso(Authentication autenticacion, @PathVariable UUID plantaId,
                              @PathVariable UUID invitadoId) {
        compartir.revocarAcceso(IdentidadDelSolicitante.id(autenticacion), plantaId, invitadoId);
    }

    // ---- del invitado ----

    /** RF-E5. */
    @GetMapping("/plantas/compartidas")
    public List<RespuestaPlanta> compartidas(Authentication autenticacion) {
        UUID solicitante = IdentidadDelSolicitante.id(autenticacion);
        return aceptacion.compartidasConmigo(solicitante).stream()
                .map(planta -> RespuestaPlanta.desde(planta, solicitante)).toList();
    }

    /** RF-E6. */
    @DeleteMapping("/plantas/compartidas/{plantaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renunciar(Authentication autenticacion, @PathVariable UUID plantaId) {
        aceptacion.renunciar(IdentidadDelSolicitante.id(autenticacion), plantaId);
    }

    // ---- de quien recibe el enlace ----

    /** RF-E2 con sesión. 410 si el enlace ya se usó, caducó o se revocó. */
    @PostMapping("/invitaciones/aceptar")
    public RespuestaPlanta aceptar(Authentication autenticacion, @Valid @RequestBody SolicitudAceptacion solicitud) {
        UUID solicitante = IdentidadDelSolicitante.id(autenticacion);
        Planta planta = aceptacion.aceptar(solicitante, solicitud.token());
        return RespuestaPlanta.desde(planta, solicitante);
    }

    /** RF-E2 sin cuenta (pública): se registra y acepta en el mismo paso. */
    @PostMapping("/invitaciones/aceptar-con-registro")
    public ResponseEntity<RespuestaAceptacionConRegistro> aceptarConRegistro(
            @Valid @RequestBody SolicitudAceptacionConRegistro solicitud) {
        AceptarInvitacionPort.AceptacionConRegistro resultado = aceptacion.registrarYAceptar(
                solicitud.token(), solicitud.nombre(), solicitud.correo(), solicitud.contrasena());
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaAceptacionConRegistro.desde(resultado));
    }
}
