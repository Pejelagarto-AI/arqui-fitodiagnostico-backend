package com.vivero.fitodiagnostico.web;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * Traduce la {@link Authentication} de Spring Security al único dato que
 * necesita la capa de aplicación: el id del usuario. Así ningún servicio
 * recibe un tipo de Spring (RA4) y ningún controlador parsea el nombre a mano.
 * El nombre de la autenticación lo fija {@link ConvertidorDeJwt} con el id.
 */
final class IdentidadDelSolicitante {

    private IdentidadDelSolicitante() { }

    static UUID id(Authentication autenticacion) {
        return UUID.fromString(autenticacion.getName());
    }
}
