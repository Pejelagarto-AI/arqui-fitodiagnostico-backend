package com.vivero.fitodiagnostico.adaptador.entrada.rest.seguridad;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El convertidor es lo que hace que revocar un rol o eliminar una cuenta surta
 * efecto sin esperar a que el token caduque: los roles salen del servicio en
 * cada petición, no del token. Sin Spring: solo el convertidor y un servicio simulado.
 */
class ConvertidorDeJwtTest {

    private final ServicioCuentas cuentas = mock(ServicioCuentas.class);
    private final ConvertidorDeJwt convertidor = new ConvertidorDeJwt(cuentas);

    private static Jwt tokenDe(String sub) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (sub != null) {
            builder.subject(sub);
        } else {
            builder.claim("otro", "x");
        }
        return builder.build();
    }

    @Test
    void losRolesSalenDelUsuarioActualYNoDelToken() {
        Usuario admin = Usuario.nuevo("Ana", "ana@vivero.co", new Credencial("h")).conRolAdministrador();
        when(cuentas.consultar(admin.id())).thenReturn(admin);

        var autenticacion = convertidor.convert(tokenDe(admin.id().toString()));

        assertThat(autenticacion.getName()).isEqualTo(admin.id().toString());
        assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CLIENTE", "ROLE_ADMINISTRADOR");
    }

    @Test
    void unRolRevocadoDejaDeAparecerConElMismoToken() {
        Usuario admin = Usuario.nuevo("Ana", "ana@vivero.co", new Credencial("h")).conRolAdministrador();
        Jwt token = tokenDe(admin.id().toString());
        when(cuentas.consultar(admin.id())).thenReturn(admin);
        assertThat(convertidor.convert(token).getAuthorities()).hasSize(2);

        when(cuentas.consultar(admin.id())).thenReturn(admin.sinRolAdministrador());

        assertThat(convertidor.convert(token).getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CLIENTE");
    }

    @Test
    void unTokenDeUnaCuentaEliminadaSeRechaza() {
        UUID id = UUID.randomUUID();
        when(cuentas.consultar(id)).thenThrow(new UsuarioNoEncontradoException(id));

        assertThatThrownBy(() -> convertidor.convert(tokenDe(id.toString())))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void unTokenSinSubOConUnSubQueNoEsUuidSeRechaza() {
        assertThatThrownBy(() -> convertidor.convert(tokenDe(null))).isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> convertidor.convert(tokenDe("no-es-uuid"))).isInstanceOf(InvalidBearerTokenException.class);
    }
}
