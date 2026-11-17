package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que la matera recibe cuando acaricia. {@code tipo} es el del evento ({@code ACARICIADA}); con
 * {@code repetida = true} el {@code id} y {@code ocurridoEn} son los de la última caricia (D172). La vitalidad y el
 * ánimo son los de ahora, para que la OLED reaccione sin otra llamada.
 */
public record RespuestaAccionDeMatera(UUID id, String tipo, Instant ocurridoEn, int vitalidad, String animo,
                                      boolean repetida) { }
