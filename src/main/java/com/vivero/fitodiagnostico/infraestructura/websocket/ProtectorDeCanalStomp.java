package com.vivero.fitodiagnostico.infraestructura.websocket;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import com.vivero.fitodiagnostico.aplicacion.ServicioPlantas;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La puerta del WebSocket (D59): quién entra y a qué se puede suscribir. Se
 * ejecuta sobre cada frame STOMP que el cliente envía.
 *
 * <ul>
 *   <li><b>CONNECT = autenticación.</b> El navegador no puede poner cabeceras
 *       en el handshake de un WebSocket, pero SÍ en el primer frame STOMP: el
 *       cliente manda {@code Authorization: Bearer <JWT>} en el CONNECT. Es el
 *       MISMO JWT de la API (misma firma, caducidad y emisor, mismo
 *       {@code JwtDecoder}), y además se comprueba que la cuenta siga existiendo
 *       (D21: eliminar la cuenta surte efecto en la siguiente conexión). Sin
 *       token válido no hay conexión.</li>
 *   <li><b>SUBSCRIBE = autorización.</b> Solo {@code /user/queue/avisos} y
 *       {@code /user/queue/plantas/{id}}, y esta última únicamente si el usuario
 *       puede LEER esa planta (dueño o invitado, por {@code ServicioPlantas.detalle},
 *       la misma regla que el GET). Una planta ajena se rechaza igual que una
 *       inexistente (D35).</li>
 *   <li><b>SEND, rechazado siempre.</b> El WebSocket es de un solo sentido:
 *       el servidor empuja y el cliente escucha. Sin esto, un cliente podría
 *       publicar frames directamente en el broker.</li>
 * </ul>
 *
 * <p>Se rechaza lanzando {@link MessagingException}: Spring responde con un frame
 * ERROR y cierra la sesión. Los servicios llegan por {@link ObjectProvider} para
 * romper el ciclo (notificador por WebSocket → plantilla de mensajes → canal de
 * entrada → este protector → servicios → notificador).
 *
 * <p>Límite: la identidad se verifica al conectar. Una conexión abierta no
 * se vuelve a comprobar cuando el token caduca (D59).
 */
@Component
public class ProtectorDeCanalStomp implements ChannelInterceptor {

    private static final String PREFIJO = "Bearer ";
    private static final String AVISOS = "/user/queue/avisos";
    private static final Pattern PLANTA = Pattern.compile("^/user/queue/plantas/([0-9a-fA-F-]{36})$");

    /** La identidad de la sesión STOMP: el nombre es el id del usuario, que es la clave de {@code convertAndSendToUser}. */
    record Sesion(String id) implements Principal {
        @Override
        public String getName() {
            return id;
        }
    }

    private final JwtDecoder decodificador;
    private final ObjectProvider<ServicioCuentas> cuentas;
    private final ObjectProvider<ServicioPlantas> plantas;

    public ProtectorDeCanalStomp(JwtDecoder decodificador, ObjectProvider<ServicioCuentas> cuentas,
                                 ObjectProvider<ServicioPlantas> plantas) {
        this.decodificador = decodificador;
        this.cuentas = cuentas;
        this.plantas = plantas;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor frame = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        StompCommand comando = frame == null ? null : frame.getCommand();
        if (comando == null) {
            return mensaje;
        }
        switch (comando) {
            case CONNECT -> autenticar(frame);
            case SUBSCRIBE -> autorizarSuscripcion(frame);
            case SEND -> throw new MessagingException("este canal es de solo lectura: el cliente no puede enviar");
            default -> { }
        }
        return mensaje;
    }

    private void autenticar(StompHeaderAccessor frame) {
        String cabecera = frame.getFirstNativeHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            throw new MessagingException("falta la cabecera Authorization: Bearer <token> en el CONNECT");
        }
        UUID usuario;
        try {
            usuario = UUID.fromString(decodificador.decode(cabecera.substring(PREFIJO.length()).trim()).getSubject());
            cuentas.getObject().consultar(usuario);
        } catch (JwtException | IllegalArgumentException | NullPointerException | UsuarioNoEncontradoException e) {
            throw new MessagingException("el token no es válido o la cuenta ya no existe");
        }
        frame.setUser(new Sesion(usuario.toString()));
    }

    private void autorizarSuscripcion(StompHeaderAccessor frame) {
        Principal sesion = frame.getUser();
        String destino = frame.getDestination();
        if (sesion == null || destino == null) {
            throw new MessagingException("hay que autenticarse antes de suscribirse");
        }
        if (AVISOS.equals(destino)) {
            return;
        }
        Matcher planta = PLANTA.matcher(destino);
        if (!planta.matches()) {
            throw new MessagingException("destino no permitido: " + destino);
        }
        try {
            plantas.getObject().detalle(UUID.fromString(sesion.getName()), UUID.fromString(planta.group(1)));
        } catch (PlantaNoEncontradaException | IllegalArgumentException e) {
            throw new MessagingException("no tienes acceso a esa planta");
        }
    }
}
