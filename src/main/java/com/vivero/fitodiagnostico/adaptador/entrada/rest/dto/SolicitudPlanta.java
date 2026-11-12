package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de registrar (RF-D1) y de editar (RF-D4) una planta: el mismo, porque
 * editar es reenviar los dos campos. Valida solo la forma; que la especie exista
 * y esté activa lo decide el dominio (RN11).
 */
public record SolicitudPlanta(

        @NotBlank @Size(max = Planta.LARGO_MAXIMO_DEL_APODO)
        String apodo,

        @NotBlank
        String especie) { }
