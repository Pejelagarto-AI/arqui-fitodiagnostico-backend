package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.dominio.excepcion.*;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

/**
 * Único punto que traduce excepciones a HTTP (RA-10). Ningún mensaje expone
 * traza, SQL ni nombre de tabla (RO-05): sólo texto de negocio o de validación.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

    @ExceptionHandler(EspecieNoEncontradaException.class)
    ProblemDetail especieNoEncontrada(EspecieNoEncontradaException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        p.setTitle("Especie no registrada");
        return p;
    }

    @ExceptionHandler(LecturaInvalidaException.class)
    ProblemDetail lecturaInvalida(LecturaInvalidaException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        p.setTitle("Lectura ambiental inconsistente");
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail solicitudInvalida(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(this::describirCampo)
                .collect(Collectors.joining("; "));
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detalle.isBlank() ? "la solicitud no cumple las validaciones requeridas" : detalle);
        p.setTitle("Solicitud inválida");
        return p;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail solicitudInvalida(HandlerMethodValidationException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "falta un parámetro o el tipo no convierte");
        p.setTitle("Solicitud inválida");
        return p;
    }

    private String describirCampo(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
