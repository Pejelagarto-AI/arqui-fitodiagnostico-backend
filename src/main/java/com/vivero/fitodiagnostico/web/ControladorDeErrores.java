package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.web.dto.ErrorRespuesta;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Reemplaza a {@code BasicErrorController} (RA1). Cualquier error que llegue
 * al contenedor servlet sin pasar por {@link ManejadorGlobalDeErrores} — una
 * ruta fuera de {@code /api} que el propio contenedor rechaza, un fallo
 * durante el renderizado de otro error — cae aquí, y aquí también se
 * responde siempre {@code application/json} con el cuerpo uniforme del
 * contrato, nunca la página blanca de error (whitelabel) de Spring Boot.
 *
 * <p>Se registra implementando {@link ErrorController}: Spring Boot solo
 * crea su {@code BasicErrorController} por defecto cuando no encuentra otro
 * bean de este tipo ({@code @ConditionalOnMissingBean}), así que este bean
 * lo desplaza por completo.
 */
@RestController
public class ControladorDeErrores implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorRespuesta> manejar(HttpServletRequest request) {
        HttpStatus status = extraerStatus(request);

        String codigo;
        String mensaje;
        switch (status) {
            case NOT_FOUND -> {
                codigo = "RECURSO_NO_ENCONTRADO";
                mensaje = "el recurso solicitado no existe.";
            }
            case METHOD_NOT_ALLOWED -> {
                codigo = "METODO_NO_PERMITIDO";
                mensaje = "el método HTTP no está permitido para este recurso.";
            }
            case BAD_REQUEST -> {
                codigo = "PARAMETRO_INVALIDO";
                mensaje = "la solicitud no es válida.";
            }
            default -> {
                codigo = "ERROR_INTERNO";
                mensaje = "Ocurrió un error inesperado.";
            }
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorRespuesta(codigo, mensaje, Map.of()));
    }

    /** Sin traza ni causa en el cuerpo (RO-05): solo el status que puso el contenedor. */
    private HttpStatus extraerStatus(HttpServletRequest request) {
        Object atributo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (atributo instanceof Integer codigoHttp) {
            HttpStatus resuelto = HttpStatus.resolve(codigoHttp);
            if (resuelto != null) {
                return resuelto;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
