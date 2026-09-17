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
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type");
    }
}
