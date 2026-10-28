package com.vivero.fitodiagnostico.adaptador.entrada.websocket;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * El handshake estándar de Spring responde un GET sin {@code Upgrade: websocket}
 * con un 400 en TEXTO PLANO y sin {@code Content-Type}. RA1 dice que este
 * backend responde únicamente JSON, así que esos dos rechazos salen con el
 * mismo cuerpo uniforme {@code {"error","mensaje","detalle"}} que el resto de la API (D59).
 * Todo lo demás del handshake queda igual.
 */
public class HandshakeConErroresJson extends DefaultHandshakeHandler {

    @Override
    protected void handleInvalidUpgradeHeader(ServerHttpRequest peticion, ServerHttpResponse respuesta) throws IOException {
        rechazar(respuesta, "este endpoint solo acepta conexiones WebSocket (cabecera Upgrade: websocket).");
    }

    @Override
    protected void handleInvalidConnectHeader(ServerHttpRequest peticion, ServerHttpResponse respuesta) throws IOException {
        rechazar(respuesta, "este endpoint solo acepta conexiones WebSocket (cabecera Connection: Upgrade).");
    }

    private static void rechazar(ServerHttpResponse respuesta, String mensaje) throws IOException {
        respuesta.setStatusCode(HttpStatus.BAD_REQUEST);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"error\":\"WEBSOCKET_REQUERIDO\",\"mensaje\":\"" + mensaje + "\",\"detalle\":{}}";
        respuesta.getBody().write(cuerpo.getBytes(StandardCharsets.UTF_8));
    }
}
