package com.vivero.fitodiagnostico.adaptador.entrada.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarLecturaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VerificarVinculoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El traductor del adaptador MQTT sin broker: mensaje -> llamada al puerto, y cada fallo -> respuesta de error con
 * el codigo del cuerpo uniforme. El puerto es un doble que anota lo que recibe; el JwtDecoder, una tabla de tokens.
 */
class SuscriptorDeLecturasMqttTest {

    private static final String SERIAL = "esp32-01";
    private static final UUID PLANTA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String MARCA = "2026-10-19T14:30:05Z";

    private record Llamada(UUID plantaId, Medicion medicion, OrigenDeMedicion origen, Instant tomadaEn) { }

    private final List<Llamada> llamadas = new ArrayList<>();
    private final List<Message<?>> publicados = new ArrayList<>();
    private RuntimeException falloDelPuerto;
    private boolean vinculado = true;
    private final VerificarVinculoPort vinculo = (serial, planta) -> {
        if (!vinculado) throw new com.vivero.fitodiagnostico.dominio.excepcion.CredencialRechazadaException(serial);
    };
    private SuscriptorDeLecturasMqtt suscriptor;
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        json = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RegistrarLecturaPort puerto = (plantaId, medicion, origen, tomadaEn) -> {
            llamadas.add(new Llamada(plantaId, medicion, origen, tomadaEn));
            if (falloDelPuerto != null) throw falloDelPuerto;
            return DiagnosticoDePlanta.deUnaEvaluacion(plantaId, origen, tomadaEn,
                    Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE));
        };
        JwtDecoder decodificador = token -> switch (token) {
            case "bueno" -> jwt(Map.of("tipo", "dispositivo", "scope", "lecturas:reportar", "sub", SERIAL,
                    "planta", PLANTA.toString()));
            case "de-otro" -> jwt(Map.of("tipo", "dispositivo", "scope", "lecturas:reportar", "sub", "otro",
                    "planta", PLANTA.toString()));
            case "de-usuario" -> jwt(Map.of("tipo", "usuario", "sub", UUID.randomUUID().toString()));
            case "sin-alcance" -> jwt(Map.of("tipo", "dispositivo", "sub", SERIAL, "planta", PLANTA.toString()));
            case "sin-planta" -> jwt(Map.of("tipo", "dispositivo", "scope", "lecturas:reportar", "sub", SERIAL));
            default -> throw new BadJwtException("firma invalida");
        };
        suscriptor = new SuscriptorDeLecturasMqtt(decodificador, puerto, vinculo, json, publicados::add);
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("t").header("alg", "HS256").claims(c -> c.putAll(claims)).build();
    }

    private static String lectura(String token, String humedad, String luz, String temperatura) {
        return "{\"token\":\"" + token + "\",\"marcaDeTiempo\":\"" + MARCA + "\",\"humedad\":" + humedad
                + ",\"luz\":" + luz + ",\"temperatura\":" + temperatura + "}";
    }

    private RespuestaMqtt procesar(String cuerpo) {
        return suscriptor.procesar(SERIAL, cuerpo.getBytes(StandardCharsets.UTF_8));
    }

    private void assertError(RespuestaMqtt r, String codigo) {
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).isEqualTo(codigo);
        assertThat(r.mensaje()).isNotBlank();
        assertThat(r.detalle()).isNotNull();
        assertThat(llamadas).as("un mensaje rechazado nunca llega al puerto").isEmpty();
    }

    // ---- el camino feliz ----

    @Test
    void unaLecturaValidaLlamaAlPuertoConLaPlantaDelTokenElOrigenDispositivoYLaHoraDelChip() {
        RespuestaMqtt r = procesar(lectura("bueno", "55", "700", "22"));

        assertThat(r.ok()).isTrue();
        assertThat(r.estado()).isEqualTo("SALUDABLE");
        assertThat(r.id()).isNotBlank();
        assertThat(r.registradaEn()).isNotBlank();
        assertThat(llamadas).hasSize(1);
        Llamada l = llamadas.get(0);
        assertThat(l.plantaId()).isEqualTo(PLANTA);
        assertThat(l.origen()).isEqualTo(OrigenDeMedicion.DISPOSITIVO);
        assertThat(l.tomadaEn()).isEqualTo(Instant.parse(MARCA));
        assertThat(l.medicion().valor(Magnitud.HUMEDAD)).isEqualTo(55.0);
        assertThat(l.medicion().valor(Magnitud.LUZ)).isEqualTo(700.0);
        assertThat(l.medicion().valor(Magnitud.TEMPERATURA)).isEqualTo(22.0);
    }

    @Test
    void laRespuestaDeExitoSerializaSoloLasClavesDelContrato() throws Exception {
        String texto = json.writeValueAsString(procesar(lectura("bueno", "55", "700", "22")));

        assertThat(json.readTree(texto).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("ok", "id", "estado", "registradaEn");
    }

    // ---- autenticacion: nada de esto llega al puerto ----

    @Test
    void sinTokenOConTokenQueNoEsTextoSeRechazaConCredencialRechazada() {
        assertError(procesar("{\"marcaDeTiempo\":\"" + MARCA + "\",\"humedad\":55,\"luz\":700,\"temperatura\":22}"),
                "CREDENCIAL_RECHAZADA");
        assertError(procesar(lectura("", "55", "700", "22")), "CREDENCIAL_RECHAZADA");
        assertError(procesar("{\"token\":42,\"humedad\":55}"), "CREDENCIAL_RECHAZADA");
    }

    @Test
    void unTokenConFirmaInvalidaOCaducadoSeRechaza() {
        assertError(procesar(lectura("falso", "55", "700", "22")), "CREDENCIAL_RECHAZADA");
    }

    @Test
    void unTokenDeUsuarioSinAlcanceOSinPlantaSeRechaza() {
        assertError(procesar(lectura("de-usuario", "55", "700", "22")), "CREDENCIAL_RECHAZADA");
        assertError(procesar(lectura("sin-alcance", "55", "700", "22")), "CREDENCIAL_RECHAZADA");
        assertError(procesar(lectura("sin-planta", "55", "700", "22")), "CREDENCIAL_RECHAZADA");
    }

    @Test
    void unDispositivoNoPuedeReportarEnElTopicoDeOtro() {
        assertError(procesar(lectura("de-otro", "55", "700", "22")), "OPERACION_NO_PERMITIDA");
    }

    // ---- forma del mensaje y valores ----

    @Test
    void unJsonRotoOQueNoEsUnObjetoEsParametroInvalido() {
        assertError(procesar("{\"token\":\"bueno\",\"humedad\": "), "PARAMETRO_INVALIDO");
        assertError(procesar("[1,2,3]"), "PARAMETRO_INVALIDO");
        assertError(procesar(""), "PARAMETRO_INVALIDO");
        assertError(procesar("no soy json"), "PARAMETRO_INVALIDO");
    }

    @Test
    void unCampoAusenteSeNombraEnElDetalle() {
        RespuestaMqtt r = procesar("{\"token\":\"bueno\",\"marcaDeTiempo\":\"" + MARCA + "\",\"humedad\":55,\"temperatura\":22}");

        assertError(r, "PARAMETRO_INVALIDO");
        assertThat(r.detalle()).containsEntry("campo", "luz");
    }

    @Test
    void unValorNoNumericoOUnaFechaMalFormadaSeNombraEnElDetalle() {
        RespuestaMqtt noNumerico = procesar(lectura("bueno", "\"abc\"", "700", "22"));
        assertError(noNumerico, "PARAMETRO_INVALIDO");
        assertThat(noNumerico.detalle()).containsEntry("campo", "humedad");

        RespuestaMqtt fecha = procesar("{\"token\":\"bueno\",\"marcaDeTiempo\":\"ayer\",\"humedad\":55,\"luz\":700,\"temperatura\":22}");
        assertError(fecha, "PARAMETRO_INVALIDO");
        assertThat(fecha.detalle()).containsEntry("campo", "marcaDeTiempo");
    }

    @Test
    void unValorFisicamenteImposibleLoRechazaElDominioConElCampo() {
        RespuestaMqtt r = procesar(lectura("bueno", "150", "700", "22"));

        assertThat(r.ok()).isFalse();
        assertThat(r.error()).isEqualTo("PARAMETRO_INVALIDO");
        assertThat(r.detalle()).containsEntry("campo", "humedad");
        assertThat(llamadas).isEmpty();
    }

    // ---- errores del puerto ----

    @Test
    void unaPlantaInexistenteResponde_PLANTA_NO_ENCONTRADA() {
        falloDelPuerto = new PlantaNoEncontradaException(PLANTA);

        RespuestaMqtt r = procesar(lectura("bueno", "55", "700", "22"));

        assertThat(r.ok()).isFalse();
        assertThat(r.error()).isEqualTo("PLANTA_NO_ENCONTRADA");
        assertThat(r.detalle()).containsEntry("id", PLANTA.toString());
    }

    @Test
    void unaExcepcionInesperadaResponde_ERROR_INTERNO_sinFiltrarElMensaje() {
        falloDelPuerto = new IllegalStateException("detalle interno secreto");

        RespuestaMqtt r = procesar(lectura("bueno", "55", "700", "22"));

        assertThat(r.error()).isEqualTo("ERROR_INTERNO");
        assertThat(r.mensaje()).doesNotContain("secreto");
    }

    // ---- el mensaje de Spring: tópico, publicación y supervivencia ----

    private Message<byte[]> mensaje(String topico, String cuerpo) {
        return MessageBuilder.withPayload(cuerpo.getBytes(StandardCharsets.UTF_8))
                .setHeader(MqttHeaders.RECEIVED_TOPIC, topico).build();
    }

    @Test
    void publicaLaRespuestaEnElTopicoDeRespuestasDelSerialDelTopicoDeEntrada() throws Exception {
        suscriptor.handleMessage(mensaje("fito/dispositivos/" + SERIAL + "/lecturas", lectura("bueno", "55", "700", "22")));

        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).getHeaders().get(MqttHeaders.TOPIC)).isEqualTo("fito/dispositivos/esp32-01/respuestas");
        assertThat(json.readTree((String) publicados.get(0).getPayload()).get("ok").asBoolean()).isTrue();
    }

    @Test
    void unTopicoInesperadoSeIgnoraSinPublicarNiLanzar() {
        suscriptor.handleMessage(mensaje("otra/cosa", "{}"));
        suscriptor.handleMessage(mensaje("fito/dispositivos//lecturas", "{}"));
        suscriptor.handleMessage(mensaje("fito/dispositivos/a/b/lecturas", "{}"));

        assertThat(publicados).isEmpty();
    }

    @Test
    void unDispositivoYaNoVinculadoSeRechazaConCredencialRechazadaYNoLlegaAlPuerto() {
        vinculado = false;

        RespuestaMqtt r = suscriptor.procesar(SERIAL, lectura("bueno", "50", "600", "24").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(r.error()).isEqualTo("CREDENCIAL_RECHAZADA");
        assertThat(llamadas).isEmpty();
    }

    @Test
    void siElPublicadorFallaElSuscriptorNoLanzaYSigueAtendiendo() {
        List<Message<?>> recibidos = new ArrayList<>();
        boolean[] primera = { true };
        SuscriptorDeLecturasMqtt fragil = new SuscriptorDeLecturasMqtt(
                token -> jwt(Map.of("tipo", "dispositivo", "scope", "lecturas:reportar", "sub", SERIAL, "planta", PLANTA.toString())),
                (p, m, o, t) -> DiagnosticoDePlanta.deUnaEvaluacion(p, o, t, Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE)),
                (s, p) -> { },
                json, m -> {
                    if (primera[0]) { primera[0] = false; throw new IllegalStateException("broker caido"); }
                    recibidos.add(m);
                });

        fragil.handleMessage(mensaje("fito/dispositivos/" + SERIAL + "/lecturas", lectura("x", "55", "700", "22")));
        fragil.handleMessage(mensaje("fito/dispositivos/" + SERIAL + "/lecturas", lectura("x", "55", "700", "22")));

        assertThat(recibidos).hasSize(1);
    }

    @Test
    void serialDeSoloAceptaLaFormaDelTopicoDeLecturas() {
        assertThat(SuscriptorDeLecturasMqtt.serialDe("fito/dispositivos/fito-7K2M9Q/lecturas")).contains("fito-7K2M9Q");
        assertThat(SuscriptorDeLecturasMqtt.serialDe("fito/dispositivos/x/respuestas")).isEmpty();
        assertThat(SuscriptorDeLecturasMqtt.serialDe("fito/dispositivos/+/lecturas")).isEmpty();
        assertThat(SuscriptorDeLecturasMqtt.serialDe(null)).isEmpty();
    }
}
