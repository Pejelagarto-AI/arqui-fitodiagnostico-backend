package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import java.time.Instant;

/** Forma exacta de la respuesta 200 (sección 2 del contrato). */
public record RespuestaDiagnostico(
        Especie especie,
        Lectura lectura,
        String estado,
        String detalle,
        Instant evaluadoEn) {

    public record Especie(String nombreCientifico, String nombreComun) { }

    public record Lectura(double temperaturaC, double humedadRelativa, int luzLux) { }

    public static RespuestaDiagnostico desde(Diagnostico diagnostico) {
        return new RespuestaDiagnostico(
                new Especie(
                        diagnostico.especie().nombreCientifico(),
                        diagnostico.especie().nombreComun()),
                new Lectura(
                        diagnostico.ambiente().temperaturaC(),
                        diagnostico.ambiente().humedadRelativa(),
                        diagnostico.ambiente().luzLux()),
                diagnostico.estado(),
                diagnostico.detalle(),
                diagnostico.evaluadoEn());
    }
}
