package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de pedir credencial: la respuesta al reto, {@code base64(HMAC-SHA256(secreto, reto))}. */
public record SolicitudCredencial(@NotBlank String respuesta) { }
