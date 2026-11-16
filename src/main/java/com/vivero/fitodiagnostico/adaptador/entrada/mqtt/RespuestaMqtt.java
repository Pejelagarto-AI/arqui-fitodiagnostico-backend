package com.vivero.fitodiagnostico.adaptador.entrada.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Lo que el backend publica en {@code fito/dispositivos/{serial}/respuestas}. MQTT no tiene respuesta por mensaje,
 * así que este tópico hace de ella. Éxito: {@code {ok: true, id, estado, registradaEn, apodo, periodo}} (el apodo de la planta, para la pantalla de la matera,
 * y {@code periodo} DIA o NOCHE para dormirla, fase 8). Error:
 * {@code {ok: false, error, mensaje, detalle}} con los mismos códigos del cuerpo uniforme HTTP (D84).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespuestaMqtt(boolean ok, String id, String estado, String registradaEn,
                            String error, String mensaje, Map<String, Object> detalle, String apodo,
                            String periodo) {

    public static RespuestaMqtt exito(String id, String estado, String registradaEn, String apodo, String periodo) {
        return new RespuestaMqtt(true, id, estado, registradaEn, null, null, null, apodo, periodo);
    }

    public static RespuestaMqtt error(String codigo, String mensaje, Map<String, Object> detalle) {
        return new RespuestaMqtt(false, null, null, null, codigo, mensaje, detalle, null, null);
    }
}
