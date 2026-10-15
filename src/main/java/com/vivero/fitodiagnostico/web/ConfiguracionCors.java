package com.vivero.fitodiagnostico.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para /api/** (RA7): el front se sirve desde un origen distinto al del
 * backend (por defecto {@code python3 -m http.server 5500} en desarrollo).
 * Los orígenes permitidos son configurables vía
 * {@code fitodiagnostico.cors.origenes} para no cablear el puerto de
 * desarrollo del front en otros ambientes.
 *
 * <p>Con Spring Security delante, el preflight lo atiende el filtro CORS de
 * seguridad (antes de la autenticación), que reutiliza esta misma
 * configuración: {@link ConfiguracionSeguridad} activa {@code cors()} sin
 * definir otra fuente, así que hay una sola lista de orígenes y métodos.
 */
@Configuration
public class ConfiguracionCors implements WebMvcConfigurer {

    private final String[] origenes;

    public ConfiguracionCors(
            @Value("${fitodiagnostico.cors.origenes:http://localhost:5500,http://127.0.0.1:5500}")
            String[] origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenes)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // Authorization: el JWT viaja en esa cabecera; sin permitirla, el navegador
                // bloquea el preflight de toda petición autenticada del front.
                .allowedHeaders("Content-Type", "Authorization");
    }
}
