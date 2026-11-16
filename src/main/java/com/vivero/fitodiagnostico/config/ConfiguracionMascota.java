package com.vivero.fitodiagnostico.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivero.fitodiagnostico.adaptador.entrada.programador.TicDeMascota;
import com.vivero.fitodiagnostico.adaptador.salida.mqtt.PublicadorDeMascotaMqtt;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.PublicadorDeMascotaPorWebSocket;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.PublicadorDeMascotaTrasCommit;
import com.vivero.fitodiagnostico.aplicacion.ObservadorDeMediciones;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.mascota.modelo.ParametrosDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.AlimentarMascotaPort;
import com.vivero.fitodiagnostico.mascota.puerto.entrada.AvanzarMascotasPort;
import com.vivero.fitodiagnostico.mascota.puerto.salida.PublicadorDeMascota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Composición del contexto {@code mascota} (fase 9). Aquí, y solo aquí, se juntan el diagnóstico y la mascota:
 *
 * <pre>
 *   ServicioMediciones ─▶ ObservadorDeMediciones (este puente, tras el commit) ─▶ AlimentarMascotaPort
 *   ServicioMascota    ─▶ PublicadorDeMascota (@Primary, tras el commit) ─▶ WebSocket | MQTT (si está encendido)
 *   TicDeMascota (cada minuto) ─▶ AvanzarMascotasPort
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(PropiedadesDeMascota.class)
public class ConfiguracionMascota {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionMascota.class);

    @Bean
    ParametrosDeMascota parametrosDeMascota(PropiedadesDeMascota propiedades) {
        return propiedades.aParametros();
    }

    /**
     * El puente de D140: la lectura se entrega a la mascota DESPUÉS del commit de la lectura y en otra transacción
     * (la de {@code ServicioMascota}, REQUIRES_NEW). Si la mascota falla, la lectura ya quedó y el dispositivo
     * recibe su 201; el error queda en el log.
     */
    @Bean
    ObservadorDeMediciones mascotaComoObservador(AlimentarMascotaPort mascota) {
        return registro -> {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        alimentar(mascota, registro);
                    }
                });
            } else {
                alimentar(mascota, registro);
            }
        };
    }

    private static void alimentar(AlimentarMascotaPort mascota, DiagnosticoDePlanta registro) {
        try {
            mascota.alRegistrarLectura(registro);
        } catch (RuntimeException e) {
            log.error("la mascota de la planta {} no pudo procesar la lectura {}", registro.plantaId(), registro.id(), e);
        }
    }

    /**
     * Los canales de los eventos de la mascota. MQTT solo si la fase 4 está encendida
     * ({@code FITO_MQTT_HABILITADO=true}): reusa su publicador de respuestas (misma conexión, QoS 1).
     */
    @Bean
    @Primary
    PublicadorDeMascota publicadorDeMascota(PublicadorDeMascotaPorWebSocket vivo,
                                            ObjectProvider<MqttPahoMessageHandler> mqtt, ObjectMapper json) {
        List<PublicadorDeMascota> canales = new ArrayList<>();
        canales.add(vivo);
        MqttPahoMessageHandler salida = mqtt.getIfAvailable();
        if (salida != null) {
            canales.add(new PublicadorDeMascotaMqtt(salida, json));
        }
        return new PublicadorDeMascotaTrasCommit(canales);
    }

    /** D141: la tarea periódica. Apagable para pruebas con {@code fitodiagnostico.mascota.tic.habilitado=false}. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "fitodiagnostico.mascota.tic", name = "habilitado", havingValue = "true",
            matchIfMissing = true)
    static class Tic {

        @Bean
        TicDeMascota ticDeMascota(AvanzarMascotasPort mascotas) {
            return new TicDeMascota(mascotas);
        }
    }
}
