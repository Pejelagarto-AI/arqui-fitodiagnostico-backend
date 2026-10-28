package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de editar datos personales: propios (RF-A4) o de otro usuario (RF-B3). */
public record SolicitudActualizarDatos(

        @NotBlank @Size(max = 120)
        String nombre,

        @NotBlank @Email @Size(max = 254)
        String correo) { }
