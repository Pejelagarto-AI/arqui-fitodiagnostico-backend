package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.adaptador.entrada.websocket.ProtectorDeCanalStomp;
import com.vivero.fitodiagnostico.adaptador.entrada.websocket.HandshakeConErroresJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * La vista en vivo (RF-G2, D59): STOMP sobre un WebSocket plano (sin SockJS)
 * en {@code /ws}, con el broker simple en memoria de Spring. NO es el canal de
 * la maceta (DA7): ese es otro adaptador y otra decisión.
 *
 * <ul>
 *   <li><b>Orígenes:</b> los mismos de CORS ({@code fitodiagnostico.cors.origenes}).
 *       CORS no cubre WebSocket; el handshake comprueba la cabecera {@code Origin}
 *       con esta lista y responde 403 a cualquier otro. Un cliente que no es un
 *       navegador no manda {@code Origin} y pasa: el origen defiende al usuario
 *       del navegador (CSWSH), no autentica; la autenticación es el JWT del CONNECT.</li>
 *   <li><b>Sentido único:</b> prefijo de aplicación {@code /app} sin ningún
 *       {@code @MessageMapping} y {@link ProtectorDeCanalStomp} rechaza cualquier SEND.</li>
 *   <li><b>Broker:</b> {@code /queue}, con destinos por usuario ({@code /user/queue/...}).
 *       En memoria: con más de una instancia del backend hace falta un broker
 *       externo (límite documentado).</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

    private final String[] origenes;
    private final ProtectorDeCanalStomp protector;

    public ConfiguracionWebSocket(
            @Value("${fitodiagnostico.cors.origenes:http://localhost:5500,http://127.0.0.1:5500}") String[] origenes,
            ProtectorDeCanalStomp protector) {
        this.origenes = origenes;
        this.protector = protector;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws")
                .setHandshakeHandler(new HandshakeConErroresJson())
                .setAllowedOrigins(origenes);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/queue");
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(protector);
    }
}
