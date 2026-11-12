package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.AutorregistrarDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.AutorregistrarDispositivoPort.Resultado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada del DISPOSITIVO para darse de alta solo (fase 3b). Ruta PÚBLICA que SOLO existe si hay
 * clave de flota configurada ({@code fitodiagnostico.flota.clave} no vacía): sin ella la ruta cae en el 404
 * uniforme y la fábrica automática queda apagada. Lo que protege es la firma, no la ruta. Sin lógica aquí:
 * traduce HTTP a {@link AutorregistrarDispositivoPort}; los errores salen por el manejador global
 * (401 FIRMA_DE_FLOTA_INVALIDA, 409 DISPOSITIVO_YA_EXISTE, 400 PARAMETRO_INVALIDO).
 *
 * <p>Vive junto a {@link AutenticacionDeDispositivoController} y repite sus propios DTO (D73).
 */
@RestController
@RequestMapping("/api/v1/dispositivos/registro")
@ConditionalOnExpression("!'${fitodiagnostico.flota.clave:}'.isEmpty()")
public class RegistroDeDispositivoController {

    private final AutorregistrarDispositivoPort registro;

    public RegistroDeDispositivoController(AutorregistrarDispositivoPort registro) {
        this.registro = registro;
    }

    /** 201 si el dispositivo es nuevo; 200 con el mismo cuerpo si ya existía con el mismo secreto (chip reiniciado). */
    @PostMapping
    public ResponseEntity<RespuestaRegistro> registrar(@Valid @RequestBody SolicitudRegistro solicitud,
                                                       HttpServletRequest peticion) {
        Resultado r = registro.registrar(solicitud.serial(), solicitud.secreto(), solicitud.firma(),
                peticion.getRemoteAddr());
        return ResponseEntity.status(r.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(RespuestaRegistro.desde(r.dispositivo()));
    }
}
