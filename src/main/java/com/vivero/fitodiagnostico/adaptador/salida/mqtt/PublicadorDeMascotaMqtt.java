package com.vivero.fitodiagnostico.adaptador.salida.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.PublicadorDeMascota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canal MQTT de los eventos de la mascota hacia la matera (D142): publica en
 * {@code fito/dispositivos/{serial}/mascota}, al lado de {@code .../lecturas} y {@code .../respuestas}, para que la
 * OLED anime (gota, sol, desmayo, corazón). El firmware lo consumirá en otro cambio.
 *
 * <p>Payload compacto, pensado para el ESP32:
 * {@code {"evento":"REGADA","ocurridoEn":"...","vitalidad":64,"animo":"FELIZ","detalle":{...}}}. QoS 1 y sin
 * retener (lo que tenga el publicador de respuestas de la fase 4, que es el que se reusa: una conexión menos).
 *
 * <p>Recibe un {@link MessageHandler} y no el cliente de Paho: en pruebas se le pasa uno que captura y no hace falta
 * broker. La composición lo crea solo si MQTT está habilitado.
 */
public class PublicadorDeMascotaMqtt implements PublicadorDeMascota {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeMascotaMqtt.class);
    private static final String PREFIJO = "fito/dispositivos/";

    private final MessageHandler salida;
    private final ObjectMapper json;

    public PublicadorDeMascotaMqtt(MessageHandler salida, ObjectMapper json) {
        this.salida = salida;
        this.json = json;
    }

    public static String topicoDe(String serial) {
        return PREFIJO + serial + "/mascota";
    }

    @Override
    public void publicar(AvisoDeMascota aviso) {
        if (aviso.seriales().isEmpty()) {
            return;
        }
        String carga = cargaDe(aviso);
        for (String serial : aviso.seriales()) {
            try {
                salida.handleMessage(MessageBuilder.withPayload(carga).setHeader(MqttHeaders.TOPIC, topicoDe(serial)).build());
            } catch (RuntimeException e) {
                log.warn("no se pudo publicar el evento de mascota {} a {}: {}", aviso.evento().tipo(), serial,
                        e.getMessage());
            }
        }
    }

    String cargaDe(AvisoDeMascota aviso) {
        Map<String, Object> carga = new LinkedHashMap<>();
        carga.put("evento", aviso.evento().tipo().name());
        carga.put("ocurridoEn", aviso.evento().ocurridoEn().toString());
        carga.put("vitalidad", aviso.vitalidad());
        carga.put("animo", aviso.animo().name());
        carga.put("detalle", aviso.evento().detalle());
        try {
            return json.writeValueAsString(carga);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("no se pudo serializar el evento de mascota", e);
        }
    }
}
