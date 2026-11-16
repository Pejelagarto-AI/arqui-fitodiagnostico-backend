package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import com.vivero.fitodiagnostico.salud.AvisadorDeSalud;
import com.vivero.fitodiagnostico.salud.SaludDelDispositivo;
import com.vivero.fitodiagnostico.salud.SensorDeMatera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Empuja {@code SALUD_DE_LA_MATERA} al dueño y a los invitados, por el mismo destino por planta que
 * {@code DIAGNOSTICO_NUEVO} ({@code /user/queue/plantas/{plantaId}}): el front ya está suscrito ahí y solo agrega un
 * caso al {@code switch} de {@code tipo}. La forma del mensaje es la de {@code NotificadorPorWebSocket.MensajeEnVivo}
 * ({@code tipo}, {@code mensaje}, {@code datos} de texto), pero el tipo es un texto: el catálogo {@code TipoDeAviso}
 * es del dominio y esta fase no lo toca (D150). Como los demás avisos, sale después del commit y un fallo no se propaga.
 */
@Component
public class AvisadorDeSaludPorWebSocket implements AvisadorDeSalud {

    public static final String TIPO = "SALUD_DE_LA_MATERA";

    /** Lo que recibe el navegador. */
    public record MensajeEnVivo(String tipo, String mensaje, Map<String, String> datos) { }

    private static final Logger log = LoggerFactory.getLogger(AvisadorDeSaludPorWebSocket.class);

    private final SimpMessageSendingOperations mensajes;

    public AvisadorDeSaludPorWebSocket(SimpMessageSendingOperations mensajes) {
        this.mensajes = mensajes;
    }

    @Override
    public void avisar(Aviso aviso) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    entregar(aviso);
                }
            });
        } else {
            entregar(aviso);
        }
    }

    private void entregar(Aviso aviso) {
        MensajeEnVivo mensaje = new MensajeEnVivo(TIPO, aviso.mensaje(), datosDe(aviso));
        String destino = "/queue/plantas/" + aviso.plantaId();
        for (UUID destinatario : aviso.destinatarios()) {
            try {
                mensajes.convertAndSendToUser(destinatario.toString(), destino, mensaje);
            } catch (RuntimeException e) {
                log.warn("no se pudo empujar {} por WebSocket: {}", TIPO, e.getMessage());
            }
        }
    }

    static Map<String, String> datosDe(Aviso aviso) {
        SaludDelDispositivo s = aviso.salud();
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("plantaId", aviso.plantaId().toString());
        datos.put("serial", s.serial());
        datos.put("estado", s.estado().name());
        for (SensorDeMatera sensor : SensorDeMatera.values()) {
            datos.put(sensor.clave(), s.sensores().get(sensor).name());
        }
        if (s.detalle() != null) datos.put("detalle", s.detalle());
        if (s.cambioEn() != null) datos.put("cambioEn", s.cambioEn().toString());
        s.ultimaNoticiaEn().ifPresent(t -> datos.put("ultimaNoticiaEn", t.toString()));
        return datos;
    }
}
