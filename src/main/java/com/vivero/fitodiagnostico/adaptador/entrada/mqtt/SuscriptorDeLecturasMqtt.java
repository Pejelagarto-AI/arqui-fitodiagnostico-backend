package com.vivero.fitodiagnostico.adaptador.entrada.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivero.fitodiagnostico.dominio.excepcion.CredencialRechazadaException;
import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarLecturaPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.VerificarVinculoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de entrada MQTT del DISPOSITIVO: hace lo mismo que {@code LecturasDeDispositivoController}, pero desde
 * un mensaje y no desde un POST, y llama al MISMO puerto {@link RegistrarLecturaPort} con origen
 * {@code DISPOSITIVO}. Es la respuesta en código a "¿y si la lectura llega por MQTT?": esta carpeta y nada del
 * dominio ni de la aplicación.
 *
 * <p>Por cada mensaje en {@code fito/dispositivos/{serial}/lecturas}:
 * <ol>
 *   <li>saca el serial del tópico y lee el JSON;</li>
 *   <li>verifica el JWT del payload con el {@link JwtDecoder} de Spring Security (firma, caducidad, emisor) y que
 *       sea de dispositivo ({@code tipo}), con alcance {@code lecturas:reportar}, planta y {@code sub == serial};</li>
 *   <li>convierte a {@link Medicion} y llama al puerto, con la planta del TOKEN y la hora que midió el chip;</li>
 *   <li>publica el resultado en {@code fito/dispositivos/{serial}/respuestas}.</li>
 * </ol>
 * Un mensaje sin token válido no llega nunca al puerto. Todo error se responde y se registra en el log; nada se
 * propaga: un mensaje malo no tumba la suscripción.
 *
 * <p>Los nombres de los claims se repiten aquí (como en {@code ConvertidorDeJwt}): un adaptador no depende de otro.
 */
public class SuscriptorDeLecturasMqtt implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SuscriptorDeLecturasMqtt.class);

    public static final String FILTRO_LECTURAS = "fito/dispositivos/+/lecturas";
    private static final String PREFIJO = "fito/dispositivos/";

    private static final String CLAIM_TIPO = "tipo";
    private static final String TIPO_DISPOSITIVO = "dispositivo";
    private static final String CLAIM_ALCANCE = "scope";
    private static final String ALCANCE_REPORTAR_LECTURAS = "lecturas:reportar";
    private static final String CLAIM_PLANTA = "planta";

    private final JwtDecoder decodificador;
    private final RegistrarLecturaPort lecturas;
    private final VerificarVinculoPort vinculo;
    private final ObjectMapper json;
    private final MessageHandler publicador;

    /** @param publicador salida hacia el broker: recibe un mensaje con el tópico en {@link MqttHeaders#TOPIC}. */
    public SuscriptorDeLecturasMqtt(JwtDecoder decodificador, RegistrarLecturaPort lecturas, VerificarVinculoPort vinculo,
                                    ObjectMapper json, MessageHandler publicador) {
        this.decodificador = decodificador;
        this.lecturas = lecturas;
        this.vinculo = vinculo;
        this.json = json;
        this.publicador = publicador;
    }

    /** {@code fito/dispositivos/{serial}/lecturas} -> el serial; vacío si el tópico no tiene esa forma. */
    public static Optional<String> serialDe(String topico) {
        if (topico == null || !topico.startsWith(PREFIJO) || !topico.endsWith("/lecturas")
                || topico.length() <= PREFIJO.length() + "/lecturas".length()) {
            return Optional.empty();
        }
        String serial = topico.substring(PREFIJO.length(), topico.length() - "/lecturas".length());
        return serial.contains("/") || serial.contains("+") || serial.contains("#")
                ? Optional.empty() : Optional.of(serial);
    }

    public static String topicoDeRespuestas(String serial) {
        return PREFIJO + serial + "/respuestas";
    }

    @Override
    public void handleMessage(Message<?> mensaje) {
        String topico = String.valueOf(mensaje.getHeaders().get(MqttHeaders.RECEIVED_TOPIC));
        try {
            Optional<String> serial = serialDe(topico);
            if (serial.isEmpty()) {
                log.warn("mensaje MQTT en un tópico inesperado, se ignora: {}", topico);
                return;
            }
            RespuestaMqtt respuesta = procesar(serial.get(), bytesDe(mensaje.getPayload()));
            publicar(serial.get(), respuesta);
        } catch (Throwable e) {   // incluso un Error: el suscriptor sigue vivo para el próximo mensaje
            log.error("fallo atendiendo un mensaje MQTT del tópico {}", topico, e);
        }
    }

    /**
     * Traduce un mensaje a la respuesta que se le publica; es el núcleo del adaptador y no necesita broker. No lanza:
     * cada fallo se convierte en una {@link RespuestaMqtt} de error con los códigos del cuerpo uniforme HTTP.
     */
    public RespuestaMqtt procesar(String serial, byte[] payload) {
        try {
            JsonNode raiz = leerObjeto(payload);
            if (raiz == null) {
                return rechazar("PARAMETRO_INVALIDO", "el mensaje no es un JSON válido.", serial, Map.of());
            }
            JsonNode token = raiz.get("token");
            if (token == null || !token.isTextual() || token.asText().isBlank()) {
                return rechazar("CREDENCIAL_RECHAZADA", "el mensaje no trae token.", serial, Map.of());
            }
            Jwt jwt = verificar(token.asText());
            if (jwt == null) {
                return rechazar("CREDENCIAL_RECHAZADA", "el token no es válido o caducó.", serial, Map.of());
            }
            // Un dispositivo no puede reportar por otro (igual que el 403 de HTTP).
            if (!serial.equals(jwt.getSubject())) {
                return rechazar("OPERACION_NO_PERMITIDA", "el token pertenece a otro dispositivo.", serial, Map.of());
            }
            UUID plantaId = UUID.fromString(jwt.getClaimAsString(CLAIM_PLANTA));
            vinculo.exigirVinculo(serial, plantaId);   // D96: el token puede sobrevivir a la desvinculación

            LecturaMqtt lectura = json.treeToValue(raiz, LecturaMqtt.class);
            Optional<String> ausente = campoAusente(lectura);
            if (ausente.isPresent()) {
                return rechazar("PARAMETRO_INVALIDO", "falta o es inválido el campo '" + ausente.get() + "'.",
                        serial, Map.of("campo", ausente.get()));
            }
            Medicion medicion = Medicion.de(
                    new Lectura(Magnitud.HUMEDAD, lectura.humedad()),
                    new Lectura(Magnitud.LUZ, lectura.luz()),
                    new Lectura(Magnitud.TEMPERATURA, lectura.temperatura()));
            DiagnosticoDePlanta registro = lecturas.registrar(plantaId, medicion, OrigenDeMedicion.DISPOSITIVO,
                    lectura.marcaDeTiempo());
            log.info("lectura MQTT de {} registrada: {}", serial, registro.diagnostico().estado());
            return RespuestaMqtt.exito(registro.id().toString(), registro.diagnostico().estado().name(),
                    registro.registradoEn().toString());
        } catch (JsonMappingException e) {
            String campo = e.getPath().isEmpty() ? null : e.getPath().get(0).getFieldName();
            return campo == null
                    ? rechazar("PARAMETRO_INVALIDO", "el mensaje no es un JSON válido.", serial, Map.of())
                    : rechazar("PARAMETRO_INVALIDO", "el campo '" + campo + "' no tiene un formato válido.", serial,
                            Map.of("campo", campo));
        } catch (LecturaInvalidaException e) {
            return rechazar("PARAMETRO_INVALIDO", e.getMessage(), serial,
                    Map.of("campo", e.getMagnitud().name().toLowerCase(Locale.ROOT)));
        } catch (CredencialRechazadaException e) {
            return rechazar("CREDENCIAL_RECHAZADA", "el dispositivo ya no está vinculado a esa planta; renueva la credencial.", serial, Map.of());
        } catch (PlantaNoEncontradaException e) {
            return rechazar("PLANTA_NO_ENCONTRADA", e.getMessage(), serial, Map.of("id", e.getId().toString()));
        } catch (IllegalArgumentException e) {
            return rechazar("PARAMETRO_INVALIDO", e.getMessage(), serial, Map.of());
        } catch (JsonProcessingException e) {
            return rechazar("PARAMETRO_INVALIDO", "el mensaje no es un JSON válido.", serial, Map.of());
        } catch (RuntimeException e) {
            log.error("error inesperado atendiendo una lectura MQTT de {}", serial, e);
            return RespuestaMqtt.error("ERROR_INTERNO", "Ocurrió un error inesperado.", Map.of());
        }
    }

    /** El JSON como objeto, o null si está roto o no es un objeto. */
    private JsonNode leerObjeto(byte[] payload) {
        try {
            JsonNode raiz = json.readTree(payload);
            return raiz != null && raiz.isObject() ? raiz : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** El JWT verificado y coherente (dispositivo, alcance, planta), o null si no sirve. */
    private Jwt verificar(String token) {
        Jwt jwt;
        try {
            jwt = decodificador.decode(token);   // firma, caducidad y emisor: la misma configuración que HTTP
        } catch (JwtException e) {
            log.warn("token MQTT rechazado: {}", e.getMessage());
            return null;
        }
        if (!TIPO_DISPOSITIVO.equals(jwt.getClaimAsString(CLAIM_TIPO))
                || !ALCANCE_REPORTAR_LECTURAS.equals(jwt.getClaimAsString(CLAIM_ALCANCE))
                || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            log.warn("token MQTT rechazado: no es un token de dispositivo con alcance de lecturas");
            return null;
        }
        try {
            UUID.fromString(jwt.getClaimAsString(CLAIM_PLANTA));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("token MQTT rechazado: no identifica una planta");
            return null;
        }
        return jwt;
    }

    private static Optional<String> campoAusente(LecturaMqtt l) {
        if (l.humedad() == null) return Optional.of("humedad");
        if (l.luz() == null) return Optional.of("luz");
        if (l.temperatura() == null) return Optional.of("temperatura");
        if (l.marcaDeTiempo() == null) return Optional.of("marcaDeTiempo");
        return Optional.empty();
    }

    private RespuestaMqtt rechazar(String codigo, String mensaje, String serial, Map<String, Object> detalle) {
        log.warn("lectura MQTT de {} rechazada: {} ({})", serial, codigo, mensaje);
        return RespuestaMqtt.error(codigo, mensaje, detalle);
    }

    private void publicar(String serial, RespuestaMqtt respuesta) throws JsonProcessingException {
        publicador.handleMessage(MessageBuilder.withPayload(json.writeValueAsString(respuesta))
                .setHeader(MqttHeaders.TOPIC, topicoDeRespuestas(serial))
                .build());
    }

    private static byte[] bytesDe(Object payload) {
        return payload instanceof byte[] b ? b : String.valueOf(payload).getBytes(StandardCharsets.UTF_8);
    }
}
