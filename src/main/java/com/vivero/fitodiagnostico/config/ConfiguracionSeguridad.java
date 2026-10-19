package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.adaptador.salida.seguridad.EmisorDeTokensJwt;
import com.vivero.fitodiagnostico.adaptador.entrada.websocket.ProtectorDeCanalStomp;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.seguridad.ManejadorDeErroresDeSeguridad;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.seguridad.ConvertidorDeJwt;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.ManejadorGlobalDeErrores;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.ConsultarCuentaPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Qué rutas son públicas, cuáles exigen sesión y cuáles exigen el rol
 * ADMINISTRADOR. La autenticación es un JWT bearer sin estado en el servidor
 * (el token lo emite {@code EmisorDeTokensJwt} y lo valida el {@code JwtDecoder}
 * de infraestructura; aquí solo se declara la política).
 *
 * <p>Públicas, y por qué: {@code POST /diagnosticos} (RF-F6, D6: probar antes
 * de registrarse), {@code GET /especies} (el formulario necesita el listado),
 * registro e inicio de sesión (nadie tiene token todavía), recuperación de
 * contraseña, aceptar invitación registrándose, el handshake del WebSocket
 * (se autentica en el primer frame, D59) y {@code /error}
 * (si exigiera sesión, el contenedor respondería 401 a cualquier error interno
 * en vez de su cuerpo JSON). Todo lo demás exige token.
 *
 * <p>Decisiones de borde:
 * <ul>
 *   <li>CSRF desactivado: el token viaja en la cabecera {@code Authorization},
 *       que el navegador no adjunta solo; CSRF defiende cookies de sesión y
 *       aquí no hay ninguna.</li>
 *   <li>Sin {@code formLogin} ni {@code httpBasic}: ambos generan páginas HTML
 *       (RA1) y no hacen falta.</li>
 *   <li>401 y 403 con cuerpo JSON uniforme: {@link ManejadorDeErroresDeSeguridad},
 *       porque son filtros y no pasan por {@link ManejadorGlobalDeErrores}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ObjectMapper objectMapper,
                                          ConsultarCuentaPort cuentas) throws Exception {
        ManejadorDeErroresDeSeguridad errores = new ManejadorDeErroresDeSeguridad(objectMapper);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())   // reutiliza ConfiguracionCors (WebMvcConfigurer)
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(rutas -> rutas
                // El preflight CORS no lleva credenciales; CorsFilter lo atiende antes, esto es cinturón.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/diagnosticos").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/especies").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/sesion").permitAll()
                // Bloque 4: quien olvidó su contraseña y quien acepta una invitación sin tener cuenta
                // todavía no pueden tener sesión.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/recuperacion", "/api/v1/auth/restablecimiento",
                        "/api/v1/invitaciones/aceptar-con-registro").permitAll()
                // El handshake del WebSocket no lleva Authorization (el navegador no puede ponerla):
                // la autenticación es el JWT del frame CONNECT (ProtectorDeCanalStomp).
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errores)
                .accessDeniedHandler(errores))
            .oauth2ResourceServer(oauth -> oauth
                .authenticationEntryPoint(errores)
                .accessDeniedHandler(errores)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new ConvertidorDeJwt(cuentas))));

        return http.build();
    }
}
