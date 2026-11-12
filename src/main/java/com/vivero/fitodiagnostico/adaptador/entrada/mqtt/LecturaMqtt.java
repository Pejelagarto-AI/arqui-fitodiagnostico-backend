package com.vivero.fitodiagnostico.adaptador.entrada.mqtt;

import java.time.Instant;

/**
 * Payload JSON que publica el dispositivo en {@code fito/dispositivos/{serial}/lecturas}. El broker es anónimo
 * (D83), así que el token de dispositivo viaja AQUÍ, en cada mensaje. Los valores son objetos (no primitivos) para
 * distinguir "ausente" de "cero": ausencia y rango físico los decide el adaptador y el dominio, no Jackson.
 *
 * <p>Duplicado a propósito de {@code adaptador.entrada.dispositivo.SolicitudLectura} (D73): un adaptador de
 * entrada no importa a otro; si HTTP y MQTT divergen, cada uno cambia solo.
 */
public record LecturaMqtt(String token, Instant marcaDeTiempo, Double humedad, Double luz, Double temperatura) { }
