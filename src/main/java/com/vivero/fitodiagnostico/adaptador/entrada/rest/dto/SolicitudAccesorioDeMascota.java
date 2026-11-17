package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

/** {@code {"accesorio": "NINGUNO" | "SOMBRERO" | "MONO" | "GAFAS"}}. Texto y no enum, para responder 400 con un mensaje propio. */
public record SolicitudAccesorioDeMascota(String accesorio) { }
