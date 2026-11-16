package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.PublicadorDeMascota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Canal de la vista en vivo para los eventos de la mascota (D142). Mismo transporte y mismo destino que
 * {@link NotificadorPorWebSocket}: {@code /user/queue/plantas/{plantaId}}, por persona, con la suscripción ya
 * protegida por {@code ProtectorDeCanalStomp} (solo quien puede leer la planta). El front distingue por
 * {@code tipo = "EVENTO_DE_MASCOTA"}.
 *
 * <p>JSON: {@code {"tipo","mensaje","datos","detalle"}}. {@code datos} lleva lo que cualquier evento trae (planta,
 * evento, id, hora, vitalidad, ánimo); {@code detalle}, lo propio de ese tipo (por ejemplo la humedad de referencia
 * de una REGADA). Mismo sobre que los avisos de siempre más un campo.
 */
@Component
public class PublicadorDeMascotaPorWebSocket implements PublicadorDeMascota {

    public static final String TIPO = "EVENTO_DE_MASCOTA";

    /** Lo que recibe el navegador. */
    public record MensajeDeMascota(String tipo, String mensaje, Map<String, String> datos, Map<String, String> detalle) { }

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeMascotaPorWebSocket.class);

    private final SimpMessageSendingOperations mensajes;

    public PublicadorDeMascotaPorWebSocket(SimpMessageSendingOperations mensajes) {
        this.mensajes = mensajes;
    }

    @Override
    public void publicar(AvisoDeMascota aviso) {
        MensajeDeMascota mensaje = new MensajeDeMascota(TIPO, aviso.mensaje(), datosDe(aviso), aviso.evento().detalle());
        String destino = "/queue/plantas/" + aviso.evento().plantaId();
        for (UUID destinatario : aviso.destinatarios()) {
            try {
                mensajes.convertAndSendToUser(destinatario.toString(), destino, mensaje);
            } catch (RuntimeException e) {
                log.warn("no se pudo empujar el evento de mascota {} por WebSocket: {}", aviso.evento().tipo(),
                        e.getMessage());
            }
        }
    }

    static Map<String, String> datosDe(AvisoDeMascota aviso) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("plantaId", aviso.evento().plantaId().toString());
        datos.put("evento", aviso.evento().tipo().name());
        datos.put("eventoId", aviso.evento().id().toString());
        datos.put("ocurridoEn", aviso.evento().ocurridoEn().toString());
        datos.put("vitalidad", String.valueOf(aviso.vitalidad()));
        datos.put("animo", aviso.animo().name());
        return datos;
    }
}
