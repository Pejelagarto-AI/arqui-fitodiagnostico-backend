package com.vivero.fitodiagnostico.adaptador.entrada.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Lo que el backend publica en {@code fito/dispositivos/{serial}/respuestas}. MQTT no tiene respuesta por mensaje,
 * así que este tópico hace de ella. Éxito: {@code {ok: true, id, estado, registradaEn}}. Error:
 * {@code {ok: false, error, mensaje, detalle}} con los mismos códigos del cuerpo uniforme HTTP (D84).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespuestaMqtt(boolean ok, String id, String estado, String registradaEn,
                            String error, String mensaje, Map<String, Object> detalle) {

    public static RespuestaMqtt exito(String id, String estado, String registradaEn) {
        return new RespuestaMqtt(true, id, estado, registradaEn, null, null, null);
    }

    public static RespuestaMqtt error(String codigo, String mensaje, Map<String, Object> detalle) {
        return new RespuestaMqtt(false, null, null, null, codigo, mensaje, detalle);
    }
}
