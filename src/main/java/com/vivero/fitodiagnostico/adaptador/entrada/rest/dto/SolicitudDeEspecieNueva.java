package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/solicitudes-especie} (RF-H1): la especie que se pide y por qué. */
public record SolicitudDeEspecieNueva(

        @NotBlank @Size(max = SolicitudEspecie.LARGO_MAXIMO_DEL_NOMBRE)
        String nombre,

        @Size(max = SolicitudEspecie.LARGO_MAXIMO_DEL_TEXTO)
        String justificacion) { }
