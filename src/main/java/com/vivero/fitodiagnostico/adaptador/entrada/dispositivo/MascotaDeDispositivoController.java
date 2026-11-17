package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VerificarVinculoPort;
import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import com.vivero.fitodiagnostico.mascota.modelo.ResumenDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ActuarDesdeLaMateraPort;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.ConsultarMascotaDeMateraPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * La matera actúa sobre su mascota (fase 13, D181): hoy, acariciarla con el botón. Mismo token, mismo alcance y mismas
 * comprobaciones que {@link LecturasDeDispositivoController} y {@link SaludDeDispositivoController}: alcance
 * {@code lecturas:reportar}, {@code sub} igual al dispositivo de la ruta (403 si no) y vínculo vigente con la planta del
 * token (401 si ya no). Un token de persona recibe 403 de la cadena de seguridad. Declarar un riego ({@code LA_REGUE}) es
 * de una persona y aquí responde 400.
 */
@RestController
@RequestMapping("/api/v1/dispositivos/{dispositivoId}/mascota/acciones")
public class MascotaDeDispositivoController {

    private static final String CLAIM_PLANTA = "planta";
    private static final String CLAIM_ALCANCE = "scope";
    private static final String ALCANCE_REPORTAR_LECTURAS = "lecturas:reportar";

    private final ActuarDesdeLaMateraPort acciones;
    private final ConsultarMascotaDeMateraPort mascota;
    private final VerificarVinculoPort vinculo;

    public MascotaDeDispositivoController(ActuarDesdeLaMateraPort acciones, ConsultarMascotaDeMateraPort mascota,
                                          VerificarVinculoPort vinculo) {
        this.acciones = acciones;
        this.mascota = mascota;
        this.vinculo = vinculo;
    }

    /** 201 con la caricia; 200 con la última si llegó antes del enfriamiento (D172); 400 con otro tipo. */
    @PostMapping
    public ResponseEntity<RespuestaAccionDeMatera> actuar(@AuthenticationPrincipal Jwt token,
                                                          @PathVariable String dispositivoId,
                                                          @RequestBody SolicitudAccionDeMatera solicitud) {
        if (!ALCANCE_REPORTAR_LECTURAS.equals(token.getClaimAsString(CLAIM_ALCANCE))) {
            throw new OperacionNoPermitidaException("el token no permite actuar sobre la mascota");
        }
        if (!dispositivoId.equals(token.getSubject())) {
            throw new OperacionNoPermitidaException("el token pertenece a otro dispositivo");
        }
        UUID plantaId = UUID.fromString(token.getClaimAsString(CLAIM_PLANTA));
        vinculo.exigirVinculo(dispositivoId, plantaId);
        exigirAcariciar(solicitud);

        ResultadoDeAccion resultado = acciones.acariciar(plantaId, dispositivoId);
        ResumenDeMascota ahora = mascota.paraLaMatera(plantaId, null).resumen();
        HttpStatus estado = resultado.repetida() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(new RespuestaAccionDeMatera(resultado.id(),
                TipoDeEvento.ACARICIADA.name(), resultado.creadaEn(), ahora.vitalidad(), ahora.animo().name(),
                resultado.repetida()));
    }

    private static void exigirAcariciar(SolicitudAccionDeMatera solicitud) {
        String tipo = solicitud == null || solicitud.tipo() == null ? "" : solicitud.tipo().trim().toUpperCase(Locale.ROOT);
        if (!TipoDeAccion.ACARICIAR.name().equals(tipo)) {
            throw new IllegalArgumentException("desde la matera el campo 'tipo' solo puede ser "
                    + Arrays.asList(TipoDeAccion.ACARICIAR));
        }
    }
}
