package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo (opcional al aprobar, obligatorio al rechazar) de la resolución de una
 * solicitud (RF-H4). Que sea obligatorio al rechazar lo decide el dominio.
 */
public record SolicitudResolucion(

        @Size(max = SolicitudEspecie.LARGO_MAXIMO_DEL_TEXTO)
        String respuesta) { }
