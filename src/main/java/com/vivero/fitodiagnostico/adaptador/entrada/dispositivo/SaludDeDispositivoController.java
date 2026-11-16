package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VerificarVinculoPort;
import com.vivero.fitodiagnostico.salud.ReportarSaludPort;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada del DISPOSITIVO para su salud (fase 10): la matera avisa que un sensor falla o que se
 * recuperó. Mismo alcance, mismo token y mismas comprobaciones que {@link LecturasDeDispositivoController}: el alcance
 * {@code lecturas:reportar}, el {@code sub} igual al dispositivo de la ruta (403 si no) y el vínculo vigente con la
 * planta del token (401 si ya no). Un token de persona recibe 403 de la cadena de seguridad.
 */
@RestController
@RequestMapping("/api/v1/dispositivos/{dispositivoId}/salud")
public class SaludDeDispositivoController {

    private static final String CLAIM_PLANTA = "planta";
    private static final String CLAIM_ALCANCE = "scope";
    private static final String ALCANCE_REPORTAR_LECTURAS = "lecturas:reportar";

    private final ReportarSaludPort salud;
    private final VerificarVinculoPort vinculo;

    public SaludDeDispositivoController(ReportarSaludPort salud, VerificarVinculoPort vinculo) {
        this.salud = salud;
        this.vinculo = vinculo;
    }

    /** 200 con el estado que quedó. 400 si el cuerpo es inválido; 401 si ya no está vinculada; 403 si el token no es suyo. */
    @PostMapping
    public RespuestaReporteDeSalud reportar(@AuthenticationPrincipal Jwt token, @PathVariable String dispositivoId,
                                            @Valid @RequestBody SolicitudSalud solicitud) {
        if (!ALCANCE_REPORTAR_LECTURAS.equals(token.getClaimAsString(CLAIM_ALCANCE))) {
            throw new OperacionNoPermitidaException("el token no permite reportar la salud");
        }
        if (!dispositivoId.equals(token.getSubject())) {
            throw new OperacionNoPermitidaException("el token pertenece a otro dispositivo");
        }
        UUID plantaId = UUID.fromString(token.getClaimAsString(CLAIM_PLANTA));
        vinculo.exigirVinculo(dispositivoId, plantaId);
        ReportarSaludPort.Resultado r = salud.reportar(dispositivoId, plantaId, solicitud.aSensores(),
                solicitud.detalle());
        return new RespuestaReporteDeSalud(r.estado().name(), r.cambio());
    }
}
