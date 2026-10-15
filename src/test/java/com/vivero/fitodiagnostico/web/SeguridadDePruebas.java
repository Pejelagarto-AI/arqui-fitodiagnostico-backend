package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Arma, para las rebanadas {@code @WebMvcTest}, lo mínimo de seguridad que la
 * rebanada no carga sola: la cadena real {@link ConfiguracionSeguridad} (la
 * que se está probando, no una réplica), un {@code JwtDecoder} que rechaza
 * cualquier token (las pruebas que necesitan identidad la inyectan con
 * {@code SecurityMockMvcRequestPostProcessors.jwt()}, que no pasa por el
 * decodificador) y un {@link ServicioCuentas} simulado que la cadena exige para
 * construir su convertidor. Una prueba que quiera stubear ese servicio lo
 * declara con {@code @MockBean} y reemplaza este.
 */
@TestConfiguration
@Import(ConfiguracionSeguridad.class)
public class SeguridadDePruebas {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new BadJwtException("token de prueba: ninguno es válido");
        };
    }

    @Bean
    ServicioCuentas servicioCuentas() {
        return Mockito.mock(ServicioCuentas.class);
    }
}
