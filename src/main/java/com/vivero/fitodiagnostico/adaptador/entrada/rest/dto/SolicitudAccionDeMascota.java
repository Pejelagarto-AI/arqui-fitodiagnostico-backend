package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

/** {@code {"tipo": "ACARICIAR" | "LA_REGUE"}}. Texto y no enum, para responder 400 con un mensaje propio. */
public record SolicitudAccionDeMascota(String tipo) { }
