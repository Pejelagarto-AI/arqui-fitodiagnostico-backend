package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

/**
 * Convierte un JWT ya verificado (firma, caducidad, emisor) en la identidad con
 * la que corre la petición. El token solo prueba QUIÉN es (claim {@code sub});
 * los roles se leen de la base en cada petición. Costo: una consulta por
 * petición autenticada. Beneficio: revocar el rol de administrador o eliminar
 * una cuenta surte efecto al instante y no cuando el token caduque.
 *
 * <p>No es un bean ({@code @Component}) a propósito: Spring MVC registra todo
 * bean {@code Converter} como conversor de parámetros, y este no debe serlo.
 * {@link ConfiguracionSeguridad} lo instancia.
 */
final class ConvertidorDeJwt implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ServicioCuentas cuentas;

    ConvertidorDeJwt(ServicioCuentas cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Usuario usuario = cargarUsuario(jwt);
        List<GrantedAuthority> autoridades = usuario.roles().stream()
                .<GrantedAuthority>map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.name()))
                .toList();
        return new JwtAuthenticationToken(jwt, autoridades, usuario.id().toString());
    }

    private Usuario cargarUsuario(Jwt jwt) {
        try {
            return cuentas.consultar(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("el token no identifica a un usuario");
        } catch (UsuarioNoEncontradoException e) {
            throw new InvalidBearerTokenException("la cuenta del token ya no existe");
        }
    }
}
