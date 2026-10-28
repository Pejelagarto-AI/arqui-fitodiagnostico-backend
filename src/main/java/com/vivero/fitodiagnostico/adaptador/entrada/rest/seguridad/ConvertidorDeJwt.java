package com.vivero.fitodiagnostico.adaptador.entrada.rest.seguridad;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.ConsultarCuentaPort;
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
 * la que corre la petición. Hay dos tipos de token: de usuario (por defecto) y de dispositivo. El token solo prueba QUIÉN es (claim {@code sub});
 * los roles se leen de la base en cada petición. Costo: una consulta por
 * petición autenticada. Beneficio: revocar el rol de administrador o eliminar
 * una cuenta surte efecto al instante y no cuando el token caduque.
 *
 * <p>No es un bean ({@code @Component}) a propósito: Spring MVC registra todo
 * bean {@code Converter} como conversor de parámetros, y este no debe serlo.
 * {@link ConfiguracionSeguridad} lo instancia.
 */
public final class ConvertidorDeJwt implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ConsultarCuentaPort cuentas;

    public ConvertidorDeJwt(ConsultarCuentaPort cuentas) {
        this.cuentas = cuentas;
    }

    /**
     * Contrato del token (lo escribe {@code EmisorTokenDispositivoJwt}, otro adaptador; los nombres se repiten
     * aquí a propósito: la dirección de dependencia entre adaptadores está prohibida, y esta prueba cubre que
     * coincidan).
     */
    public static final String CLAIM_TIPO = "tipo";
    public static final String TIPO_USUARIO = "usuario";
    public static final String TIPO_DISPOSITIVO = "dispositivo";
    public static final String ALCANCE_REPORTAR_LECTURAS = "lecturas:reportar";
    public static final String AUTORIDAD_REPORTAR_LECTURAS = "SCOPE_" + ALCANCE_REPORTAR_LECTURAS;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String tipo = jwt.getClaimAsString(CLAIM_TIPO);
        if (TIPO_DISPOSITIVO.equals(tipo)) {
            return convertirDispositivo(jwt);
        }
        if (tipo != null && !TIPO_USUARIO.equals(tipo)) {
            throw new InvalidBearerTokenException("tipo de token desconocido");
        }
        // Sin claim "tipo" (tokens emitidos antes de la fase 2) se deriva: es de usuario.
        return convertirUsuario(jwt);
    }

    /**
     * Un dispositivo no es un usuario: no se consulta la base, no tiene roles y su única autoridad es el
     * alcance del token. Que el token sea coherente (alcance, planta) se comprueba aquí; que ese alcance
     * sirva para la ruta lo decide la cadena de seguridad.
     */
    private AbstractAuthenticationToken convertirDispositivo(Jwt jwt) {
        String dispositivoId = jwt.getSubject();
        if (dispositivoId == null || dispositivoId.isBlank()) {
            throw new InvalidBearerTokenException("el token de dispositivo no identifica al dispositivo");
        }
        try {
            UUID.fromString(jwt.getClaimAsString("planta"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("el token de dispositivo no identifica una planta");
        }
        if (!ALCANCE_REPORTAR_LECTURAS.equals(jwt.getClaimAsString("scope"))) {
            throw new InvalidBearerTokenException("el token de dispositivo no tiene un alcance conocido");
        }
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(AUTORIDAD_REPORTAR_LECTURAS)),
                dispositivoId);
    }

    private AbstractAuthenticationToken convertirUsuario(Jwt jwt) {
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
