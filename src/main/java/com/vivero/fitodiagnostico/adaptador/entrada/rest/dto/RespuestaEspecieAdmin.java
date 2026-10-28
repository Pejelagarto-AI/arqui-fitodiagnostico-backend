package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;

import java.util.Map;

/**
 * Forma de una especie en la vista de administración: igual que
 * {@link RespuestaEspecie} más {@code activa}. Es un DTO aparte para no tocar
 * el contrato público de {@code GET /api/v1/especies} (Anexo A), donde todas
 * las especies son activas por construcción.
 */
public record RespuestaEspecieAdmin(String nombre, boolean activa, Map<String, RespuestaEspecie.Rango> rangos) {

    public static RespuestaEspecieAdmin desde(Especie especie) {
        return new RespuestaEspecieAdmin(especie.nombre(), especie.activa(), RespuestaEspecie.desde(especie).rangos());
    }
}
