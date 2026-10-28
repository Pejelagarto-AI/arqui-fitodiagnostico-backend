package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de {@link Notificador} para la vista en vivo (RF-G2, D59): empuja
 * el aviso al navegador de cada destinatario por STOMP sobre WebSocket.
 *
 * <p>Se entrega POR USUARIO ({@code convertAndSendToUser}) a los destinatarios
 * que el servicio calculó al emitir el aviso, no a un tema compartido por
 * planta. Así la autorización de la entrega es la del momento del aviso: un
 * invitado al que ya le quitaron el acceso deja de recibir aunque siga
 * suscrito (la suscripción se autorizó cuando la hizo, no se re-evalúa).
 *
 * <p>Destinos (el cliente se suscribe a {@code /user/queue/...}):
 * <ul>
 *   <li>{@code /queue/plantas/{plantaId}}: DIAGNOSTICO_NUEVO y CAMBIO_DE_ESTADO,
 *       lo que refresca la vista de esa planta. Suscribirse exige poder leerla.</li>
 *   <li>{@code /queue/avisos}: todo lo demás que llegue a este canal (ACCESO_PERDIDO,
 *       SOLICITUD_RESUELTA). No es de una planta, o ya no se puede suscribir a ella.</li>
 * </ul>
 *
 * <p>Nunca envía el token ({@link Aviso#TOKEN}) aunque el aviso lo traiga: es una
 * credencial de un solo uso y la vista en vivo no tiene por qué verla.
 */
@Component
public class NotificadorPorWebSocket implements Notificador {

    /** Lo que recibe el navegador. JSON: {@code {"tipo","mensaje","datos"}}. */
    public record MensajeEnVivo(TipoDeAviso tipo, String mensaje, Map<String, String> datos) { }

    private static final Logger log = LoggerFactory.getLogger(NotificadorPorWebSocket.class);

    private final SimpMessageSendingOperations mensajes;

    public NotificadorPorWebSocket(SimpMessageSendingOperations mensajes) {
        this.mensajes = mensajes;
    }

    @Override
    public void avisar(Aviso aviso) {
        MensajeEnVivo mensaje = new MensajeEnVivo(aviso.tipo(), aviso.mensaje(), sinToken(aviso.datos()));
        String destino = destinoDe(aviso);
        for (UUID destinatario : aviso.destinatarios()) {
            try {
                mensajes.convertAndSendToUser(destinatario.toString(), destino, mensaje);
            } catch (RuntimeException e) {
                log.warn("no se pudo empujar el aviso {} por WebSocket: {}", aviso.tipo(), e.getMessage());
            }
        }
    }

    static String destinoDe(Aviso aviso) {
        String plantaId = aviso.datos().get(Aviso.PLANTA_ID);
        boolean esDeUnaPlanta = aviso.tipo() == TipoDeAviso.DIAGNOSTICO_NUEVO
                || aviso.tipo() == TipoDeAviso.CAMBIO_DE_ESTADO;
        return esDeUnaPlanta && plantaId != null ? "/queue/plantas/" + plantaId : "/queue/avisos";
    }

    private static Map<String, String> sinToken(Map<String, String> datos) {
        Map<String, String> limpios = new HashMap<>(datos);
        limpios.remove(Aviso.TOKEN);
        return limpios;
    }
}
