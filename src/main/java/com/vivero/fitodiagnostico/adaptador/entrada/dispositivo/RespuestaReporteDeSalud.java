package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

/** Lo que el servidor devuelve a la matera: el estado de conjunto que quedó y si cambió respecto al anterior. */
public record RespuestaReporteDeSalud(String estado, boolean cambio) { }
