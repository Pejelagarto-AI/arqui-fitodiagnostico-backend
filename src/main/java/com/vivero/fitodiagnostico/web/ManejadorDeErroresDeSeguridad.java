package com.vivero.fitodiagnostico.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivero.fitodiagnostico.web.dto.ErrorRespuesta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.Map;

/**
 * Los errores 401 y 403 los produce un FILTRO de Spring Security, antes de que
 * la petición llegue a Spring MVC; por eso {@link ManejadorGlobalDeErrores}
 * (un {@code @RestControllerAdvice}) nunca los ve. Sin este manejador, Spring
 * respondería 401 sin cuerpo o dejaría que el contenedor generara su página de
 * error. Aquí se escribe el mismo cuerpo uniforme {@link ErrorRespuesta} que el
 * resto de la API, siempre {@code application/json} sea cual sea el
 * {@code Accept} del cliente (RA1: el backend nunca responde HTML).
 *
 * <p>Es una sola clase para los dos casos porque comparten todo salvo el
 * código: 401 = "no sé quién eres", 403 = "sé quién eres y no puedes".
 */
public class ManejadorDeErroresDeSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ManejadorDeErroresDeSeguridad(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: sin token, token inválido, caducado o de una cuenta que ya no existe. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException excepcion) throws IOException {
        // RFC 6750: un 401 de esquema Bearer debe anunciarlo. No se agrega el motivo
        // (error_description) para no decirle a un atacante por qué falló su token.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "se requiere iniciar sesión con un token válido.");
    }

    /** 403: autenticado, pero sin el rol que exige la ruta. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException excepcion) throws IOException {
        escribir(response, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "no tienes permiso para realizar esta operación.");
    }

    private void escribir(HttpServletResponse response, HttpStatus status, String codigo,
                          String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ErrorRespuesta(codigo, mensaje, Map.of()));
    }
}
