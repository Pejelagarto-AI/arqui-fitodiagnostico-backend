package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import java.util.Map;

/**
 * Forma uniforme de cualquier error de la API (RA6): un código de negocio
 * estable para el front, un mensaje en español para humanos y un detalle
 * estructurado. DTO de la capa web, nunca del dominio.
 */
public record ErrorRespuesta(String error, String mensaje, Map<String, Object> detalle) { }
