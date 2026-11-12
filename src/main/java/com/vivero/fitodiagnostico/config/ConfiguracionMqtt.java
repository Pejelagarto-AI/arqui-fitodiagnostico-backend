package com.vivero.fitodiagnostico.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivero.fitodiagnostico.adaptador.entrada.mqtt.SuscriptorDeLecturasMqtt;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarLecturaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VerificarVinculoPort;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composición del canal MQTT (fase 4). Todo detrás de {@code fitodiagnostico.mqtt.habilitado=true}
 * ({@code FITO_MQTT_HABILITADO}): sin la propiedad, o sin broker, la aplicación arranca igual por HTTP.
 *
 * <p>Dos conexiones al broker, con clientId distinto (un clientId solo admite una conexión a la vez):
 * <ul>
 *   <li>entrada: clientId {@code fito-backend} (D85), {@code cleanSession=false} y QoS 1: el broker guarda lo
 *       publicado mientras el backend está caído y lo entrega al volver;</li>
 *   <li>salida: {@code fito-backend-respuestas}, QoS 1, para publicar en {@code .../respuestas}.</li>
 * </ul>
 * Aquí se cablea; la lógica vive en {@link SuscriptorDeLecturasMqtt} (adaptador), que no conoce esta clase.
 */
@Configuration
@EnableIntegration
@ConditionalOnProperty(prefix = "fitodiagnostico.mqtt", name = "habilitado", havingValue = "true")
public class ConfiguracionMqtt {

    private static final int QOS = 1;
    private static final int KEEPALIVE_S = 30;

    private static MqttPahoClientFactory fabrica(String url, boolean sesionLimpia) {
        MqttConnectOptions opciones = new MqttConnectOptions();
        opciones.setServerURIs(new String[] { url });
        opciones.setCleanSession(sesionLimpia);
        opciones.setAutomaticReconnect(true);
        opciones.setKeepAliveInterval(KEEPALIVE_S);
        opciones.setConnectionTimeout(10);
        DefaultMqttPahoClientFactory fabrica = new DefaultMqttPahoClientFactory();
        fabrica.setConnectionOptions(opciones);
        return fabrica;
    }

    /** Publica en {@code .../respuestas}; el tópico viaja en la cabecera del mensaje. */
    @Bean
    public MqttPahoMessageHandler publicadorDeRespuestasMqtt(
            @Value("${fitodiagnostico.mqtt.url}") String url,
            @Value("${fitodiagnostico.mqtt.client-id}") String clientId) {
        MqttPahoMessageHandler publicador = new MqttPahoMessageHandler(clientId + "-respuestas", fabrica(url, true));
        publicador.setAsync(false);
        publicador.setDefaultQos(QOS);
        publicador.setDefaultRetained(false);
        return publicador;
    }

    @Bean
    public SuscriptorDeLecturasMqtt suscriptorDeLecturasMqtt(JwtDecoder decodificador, RegistrarLecturaPort lecturas,
                                                             VerificarVinculoPort vinculo, ObjectMapper json,
                                                             MqttPahoMessageHandler publicadorDeRespuestasMqtt) {
        return new SuscriptorDeLecturasMqtt(decodificador, lecturas, vinculo, json, publicadorDeRespuestasMqtt);
    }

    @Bean
    public MessageChannel lecturasMqttCanal(SuscriptorDeLecturasMqtt suscriptor) {
        DirectChannel canal = new DirectChannel();
        canal.subscribe(suscriptor);
        return canal;
    }

    /** Se suscribe a {@code fito/dispositivos/+/lecturas} con sesión persistente y QoS 1. */
    @Bean
    public MqttPahoMessageDrivenChannelAdapter entradaMqtt(
            @Value("${fitodiagnostico.mqtt.url}") String url,
            @Value("${fitodiagnostico.mqtt.client-id}") String clientId,
            MessageChannel lecturasMqttCanal) {
        MqttPahoMessageDrivenChannelAdapter adaptador = new AdaptadorDeEntrada(
                clientId, fabrica(url, false), SuscriptorDeLecturasMqtt.FILTRO_LECTURAS);
        DefaultPahoMessageConverter conversor = new DefaultPahoMessageConverter(QOS, false);
        conversor.setPayloadAsBytes(true);   // el JSON roto lo decide el adaptador, no un decodificador de texto
        adaptador.setConverter(conversor);
        adaptador.setQos(QOS);
        adaptador.setOutputChannel(lecturasMqttCanal);
        return adaptador;
    }

    /**
     * Igual que el de Spring Integration salvo por una cosa: la suscripcion se hace en otro hilo. Spring suscribe dentro
     * de {@code connectComplete}, que Paho llama en SU hilo de callbacks. Con sesion persistente el broker empieza a
     * entregar lo acumulado en cuanto la conexion se establece; si hay mas de unos pocos mensajes, el hilo de callbacks
     * (bloqueado esperando el SUBACK) no los procesa, la cola interna de Paho se llena, el hilo lector se frena y el
     * SUBACK no se lee: la suscripcion se vence a los 30 s con un error. Suscribir fuera de ese hilo deja que el
     * backlog se procese mientras tanto.
     */
    static final class AdaptadorDeEntrada extends MqttPahoMessageDrivenChannelAdapter {

        private final ExecutorService suscripcion = Executors.newSingleThreadExecutor(r -> {
            Thread hilo = new Thread(r, "mqtt-suscripcion");
            hilo.setDaemon(true);
            return hilo;
        });

        AdaptadorDeEntrada(String clientId, MqttPahoClientFactory fabrica, String... topicos) {
            super(clientId, fabrica, topicos);
        }

        @Override
        public void connectComplete(boolean reconexion, String servidor) {
            suscripcion.execute(() -> AdaptadorDeEntrada.super.connectComplete(reconexion, servidor));
        }

        @Override
        public void destroy() {
            suscripcion.shutdown();
            super.destroy();
        }
    }
}
