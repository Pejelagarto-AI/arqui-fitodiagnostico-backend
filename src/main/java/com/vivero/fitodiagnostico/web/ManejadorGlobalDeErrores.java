package com.vivero.fitodiagnostico.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;
import com.vivero.fitodiagnostico.web.dto.ErrorRespuesta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Único punto que traduce excepciones al cuerpo de error uniforme del
 * contrato (RA6): {@code {"error", "mensaje", "detalle"}} siempre. Ningún
 * mensaje expone traza, SQL ni nombre interno (RO-05); las excepciones no
 * mapeadas se loguean con su stacktrace pero responden ERROR_INTERNO genérico.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

    /** Orden determinista para reportar el primer campo inválido cuando hay varios (sección 2 del contrato). */
    private static final List<String> ORDEN_CAMPOS = List.of("especie", "humedad", "luz", "temperatura");

    @ExceptionHandler(EspecieNoEncontradaException.class)
    ResponseEntity<ErrorRespuesta> especieNoEncontrada(EspecieNoEncontradaException e) {
        return construir(HttpStatus.NOT_FOUND, "ESPECIE_NO_SOPORTADA", e.getMessage(),
                Map.of("especie", e.getNombre()));
    }

    @ExceptionHandler(LecturaInvalidaException.class)
    ResponseEntity<ErrorRespuesta> lecturaInvalida(LecturaInvalidaException e) {
        String campo = e.getMagnitud().name().toLowerCase(Locale.ROOT);
        return construir(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", e.getMessage(),
                Map.of("campo", campo));
    }

    /** Bean Validation (@NotBlank/@NotNull): campo ausente, nulo o en blanco. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorRespuesta> parametroAusente(MethodArgumentNotValidException e) {
        String campo = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .min(Comparator.comparingInt(this::posicionEnOrden))
                .orElse("desconocido");
        return construir(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "falta o es inválido el campo '" + campo + "'.", Map.of("campo", campo));
    }

    /**
     * Cuerpo que no deserializa: valor no numérico en un campo (Jackson lanza
     * {@link MismatchedInputException}, superclase de InvalidFormatException,
     * con la ruta del campo que falló) o JSON sintácticamente inválido.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorRespuesta> cuerpoIlegible(HttpMessageNotReadableException e) {
        String campo = campoDesdeCausa(e.getCause());
        if (campo != null) {
            return construir(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                    "el campo '" + campo + "' no tiene un formato válido.", Map.of("campo", campo));
        }
        return construir(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "el cuerpo de la solicitud no es un JSON válido.", Map.of());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseEntity<ErrorRespuesta> rutaNoEncontrada(NoHandlerFoundException e) {
        return construir(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO",
                "el recurso solicitado no existe.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorRespuesta> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "el método HTTP no está permitido para este recurso.", Map.of());
    }

    /** Cualquier otra excepción: 500 genérico, sin traza en el cuerpo; se loguea completa. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorRespuesta> errorInterno(Exception e) {
        log.error("error inesperado atendiendo la solicitud", e);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado.", Map.of());
    }

    private int posicionEnOrden(String campo) {
        int posicion = ORDEN_CAMPOS.indexOf(campo);
        return posicion < 0 ? ORDEN_CAMPOS.size() : posicion;
    }

    private String campoDesdeCausa(Throwable causa) {
        if (causa instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            JsonMappingException.Reference primero = mie.getPath().get(0);
            return primero.getFieldName();
        }
        return null;
    }

    /**
     * {@code Content-Type} explícito (RA1): si se deja que la negociación de
     * contenido decida, una solicitud con {@code Accept: text/html} no
     * encuentra un conversor compatible con este cuerpo JSON, lanza
     * {@link org.springframework.web.HttpMediaTypeNotAcceptableException} y
     * termina en la página de error por defecto de Spring Boot (HTML). Fijar
     * el tipo aquí hace que el conversor de Jackson se use sin negociar,
     * pase lo que pida el cliente.
     */
    private ResponseEntity<ErrorRespuesta> construir(HttpStatus status, String codigo, String mensaje,
                                                       Map<String, Object> detalle) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorRespuesta(codigo, mensaje, detalle));
    }
}
