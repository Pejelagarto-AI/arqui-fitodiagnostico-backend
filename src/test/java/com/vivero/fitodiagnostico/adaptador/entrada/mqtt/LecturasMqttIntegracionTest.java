package com.vivero.fitodiagnostico.adaptador.entrada.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorTokenDispositivoPort;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El flujo completo con el contexto real y un broker MQTT EMBEBIDO (Moquette, sin Docker ni Testcontainers): un
 * "chip" (cliente Paho) publica en {@code fito/dispositivos/{serial}/lecturas} y lee {@code .../respuestas}; lo que
 * se comprueba es la respuesta, el diagnostico guardado con origen DISPOSITIVO y que el suscriptor sigue vivo
 * despues de cada mensaje malo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LecturasMqttIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String SERIAL = "esp32-m1";
    private static final String MARCA = "2026-10-19T14:30:05Z";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int puerto = puertoLibre();

    private static int puertoLibre() {
        try (ServerSocket libre = new ServerSocket(0)) {
            return libre.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * El broker es un bean del contexto de prueba: Spring detiene primero los adaptadores MQTT (que se desconectan
     * con el broker vivo) y despues lo destruye. Con @BeforeAll/@AfterAll el broker moria antes y el cierre del
     * contexto se quedaba esperando una desconexion que nunca llegaba.
     */
    @TestConfiguration
    static class BrokerEmbebido {
        @Bean(destroyMethod = "stopServer")
        Server brokerMoquette() throws IOException {
            Properties p = new Properties();
            p.setProperty("host", "127.0.0.1");
            p.setProperty("port", String.valueOf(puerto));
            p.setProperty("allow_anonymous", "true");
            p.setProperty("persistence_enabled", "false");
            p.setProperty("telemetry_enabled", "false");
            p.setProperty("data_path", Files.createTempDirectory("moquette-fito").toString());
            Server broker = new Server();
            broker.startServer(new MemoryConfig(p));
            return broker;
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("fitodiagnostico.mqtt.habilitado", () -> "true");
        registro.add("fitodiagnostico.mqtt.url", () -> "tcp://127.0.0.1:" + puerto);
        registro.add("fitodiagnostico.mqtt.client-id", () -> "fito-backend-test");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmisorTokenDispositivoPort emisor;

    private final ConcurrentLinkedQueue<String[]> respuestas = new ConcurrentLinkedQueue<>();   // {topico, cuerpo}
    private MqttClient chip;
    private String planta;
    private String tokenValido;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        String dueno = registrarEIniciarSesion();
        planta = leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"Pepa\",\"especie\":\"potos\"}")).andExpect(status().isCreated()), "$.id");
        // Desde la D96 una lectura exige que el dispositivo siga reclamado por la planta del token.
        jdbc.update("insert into dispositivo (serial, secreto, planta_id, reclamado_en, creado_en, registrado_por) "
                + "values (?, 's', ?, now(), now(), 'ADMIN')", SERIAL, UUID.fromString(planta));
        tokenValido = emisor.emitir(SERIAL, UUID.fromString(planta)).valor();

        chip = new MqttClient("tcp://127.0.0.1:" + puerto, "chip-" + UUID.randomUUID(), new MemoryPersistence());
        MqttConnectOptions opciones = new MqttConnectOptions();
        opciones.setCleanSession(true);
        chip.connect(opciones);
        chip.subscribe("fito/dispositivos/+/respuestas", 1,
                (topico, mensaje) -> respuestas.add(new String[] { topico, new String(mensaje.getPayload(), StandardCharsets.UTF_8) }));
        esperarAQueElBackendEscuche();
    }

    @AfterEach
    void limpiar() throws Exception {
        if (chip != null && chip.isConnected()) chip.disconnect();
        if (chip != null) chip.close();
        jdbc.update("delete from dispositivo where serial = ?", SERIAL);
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'mqtt-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'mqtt-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'mqtt-%')");
        jdbc.update("delete from usuario where correo like 'mqtt-%'");
    }

    // ---- los casos ----

    @Test
    void unaLecturaValidaRecibeRespuestaOkYQuedaGuardadaConOrigenDispositivoYSuHoraOriginal() throws Exception {
        publicar(SERIAL, lectura(tokenValido, "55", "700", "22"));

        JsonNode r = respuestaDe(SERIAL);
        assertThat(r.get("ok").asBoolean()).isTrue();
        assertThat(r.get("estado").asText()).isEqualTo("SALUDABLE");
        assertThat(r.get("id").asText()).isNotBlank();
        assertThat(r.get("registradaEn").asText()).isNotBlank();
        assertThat(r.get("apodo").asText()).isEqualTo("Pepa");

        Map<String, Object> fila = jdbc.queryForMap(
                "select origen, tomada_en from diagnostico_planta where planta_id = ?", UUID.fromString(planta));
        assertThat(fila.get("origen")).isEqualTo("DISPOSITIVO");
        assertThat(fila.get("tomada_en").toString()).contains("2026-10-19");
        assertThat(diagnosticos()).isEqualTo(1);
    }

    @Test
    void unTokenInvalidoRecibeCredencialRechazadaYNoGuardaNada() throws Exception {
        publicar(SERIAL, lectura("esto.no.es.un.jwt", "55", "700", "22"));

        JsonNode r = respuestaDe(SERIAL);
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("error").asText()).isEqualTo("CREDENCIAL_RECHAZADA");
        assertThat(diagnosticos()).isZero();
    }

    @Test
    void unTokenDeOtroDispositivoRecibeOperacionNoPermitida() throws Exception {
        String ajeno = emisor.emitir("otro-chip", UUID.fromString(planta)).valor();
        publicar(SERIAL, lectura(ajeno, "55", "700", "22"));

        assertThat(respuestaDe(SERIAL).get("error").asText()).isEqualTo("OPERACION_NO_PERMITIDA");
        assertThat(diagnosticos()).isZero();
    }

    @Test
    void trasDesvincularLaMateraElTokenVigenteRecibeCredencialRechazada() throws Exception {
        jdbc.update("update dispositivo set planta_id = null, reclamado_en = null where serial = ?", SERIAL);
        publicar(SERIAL, lectura(tokenValido, "55", "700", "22"));

        JsonNode r = respuestaDe(SERIAL);
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("error").asText()).isEqualTo("CREDENCIAL_RECHAZADA");
        assertThat(diagnosticos()).isZero();
    }

    @Test
    void unParametroInvalidoRecibeParametroInvalidoConElCampo() throws Exception {
        publicar(SERIAL, lectura(tokenValido, "150", "700", "22"));

        JsonNode r = respuestaDe(SERIAL);
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("error").asText()).isEqualTo("PARAMETRO_INVALIDO");
        assertThat(r.get("detalle").get("campo").asText()).isEqualTo("humedad");
        assertThat(diagnosticos()).isZero();
    }

    @Test
    void unJsonRotoSeResponde_y_elSuscriptorSigueVivoParaLaSiguienteLectura() throws Exception {
        publicar(SERIAL, "{\"token\":\"x\",\"humedad\": ");
        JsonNode rota = respuestaDe(SERIAL);
        assertThat(rota.get("error").asText()).isEqualTo("PARAMETRO_INVALIDO");

        // Despues del fallo, otros dos malos y uno bueno: el suscriptor no se cayo.
        publicar(SERIAL, "no soy json");
        respuestaDe(SERIAL);
        publicar(SERIAL, lectura("esto.no.es.un.jwt", "55", "700", "22"));
        respuestaDe(SERIAL);
        publicar(SERIAL, lectura(tokenValido, "50", "600", "21"));

        assertThat(respuestaDe(SERIAL).get("ok").asBoolean()).isTrue();
        assertThat(diagnosticos()).isEqualTo(1);
    }

    // ---- utilidades ----

    private static String lectura(String token, String humedad, String luz, String temperatura) {
        return "{\"token\":\"" + token + "\",\"marcaDeTiempo\":\"" + MARCA + "\",\"humedad\":" + humedad
                + ",\"luz\":" + luz + ",\"temperatura\":" + temperatura + "}";
    }

    private void publicar(String serial, String cuerpo) throws Exception {
        MqttMessage mensaje = new MqttMessage(cuerpo.getBytes(StandardCharsets.UTF_8));
        mensaje.setQos(1);
        chip.publish("fito/dispositivos/" + serial + "/lecturas", mensaje);
    }

    /** La primera respuesta pendiente de ese serial (en orden), o falla a los 10 s. */
    private JsonNode respuestaDe(String serial) {
        String topico = "fito/dispositivos/" + serial + "/respuestas";
        String[][] encontrada = new String[1][];
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() -> {
            for (String[] r : respuestas) {
                if (r[0].equals(topico)) { encontrada[0] = r; return true; }
            }
            return false;
        });
        respuestas.remove(encontrada[0]);
        try {
            return JSON.readTree(encontrada[0][1]);
        } catch (IOException e) {
            throw new AssertionError("la respuesta no es JSON: " + encontrada[0][1], e);
        }
    }

    /** El broker no guarda para nadie lo publicado antes de que el backend se suscriba: se sondea hasta que conteste. */
    private void esperarAQueElBackendEscuche() {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).until(() -> {
            publicar("sonda", "sonda");
            Thread.sleep(300);
            return respuestas.stream().anyMatch(r -> r[0].equals("fito/dispositivos/sonda/respuestas"));
        });
        respuestas.clear();
    }

    private int diagnosticos() {
        return jdbc.queryForObject("select count(*) from diagnostico_planta where planta_id = ?", Integer.class,
                UUID.fromString(planta));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder p, String cuerpo) {
        return p.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder p, String token) {
        return p.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String leer(ResultActions r, String ruta) throws Exception {
        return String.valueOf((Object) JsonPath.read(r.andReturn().getResponse().getContentAsString(), ruta));
    }

    private String registrarEIniciarSesion() throws Exception {
        String correo = "mqtt-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Mqtt\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }
}
