package com.vivero.fitodiagnostico.adaptador.salida.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivero.fitodiagnostico.mascota.modelo.Animo;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El canal MQTT de la mascota hacia la matera, sin broker: el MessageHandler captura lo que se publicaría. */
class PublicadorDeMascotaMqttTest {

    private final ObjectMapper json = new ObjectMapper();
    private final List<Message<?>> publicados = new ArrayList<>();
    private final PublicadorDeMascotaMqtt publicador = new PublicadorDeMascotaMqtt(publicados::add, json);

    private AvisoDeMascota aviso(Set<String> seriales) {
        EventoDeMascota evento = new EventoDeMascota(UUID.randomUUID(), UUID.randomUUID(), TipoDeEvento.DESMAYO,
                Instant.parse("2026-11-16T08:05:00Z"), Map.of("estado", "CRITICO"));
        return new AvisoDeMascota(evento, "Pepa", 0, Animo.DESMAYADA, Set.of(UUID.randomUUID()), seriales);
    }

    @Test
    void publicaUnMensajePorMateraEnSuTopicoDeMascota() throws Exception {
        publicador.publicar(aviso(Set.of("fito-AAA111")));

        assertThat(publicados).singleElement().satisfies(m -> {
            assertThat(m.getHeaders().get(MqttHeaders.TOPIC)).isEqualTo("fito/dispositivos/fito-AAA111/mascota");
            JsonNode carga = json.readTree((String) m.getPayload());
            assertThat(carga.get("evento").asText()).isEqualTo("DESMAYO");
            assertThat(carga.get("vitalidad").asInt()).isZero();
            assertThat(carga.get("animo").asText()).isEqualTo("DESMAYADA");
            assertThat(carga.get("ocurridoEn").asText()).isEqualTo("2026-11-16T08:05:00Z");
            assertThat(carga.get("detalle").get("estado").asText()).isEqualTo("CRITICO");
        });
    }

    @Test
    void sinMateraVinculadaNoPublicaNada() {
        publicador.publicar(aviso(Set.of()));

        assertThat(publicados).isEmpty();
    }
}
