package com.vivero.fitodiagnostico.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * RA1: el backend responde siempre JSON, también cuando el cliente lo pide de
 * otra forma. Con la negociación por defecto, una petición con
 * {@code Accept: text/html} (un navegador que abre la URL a mano) a un endpoint
 * que devuelve un objeto no encuentra un conversor compatible y Spring lanza
 * {@code HttpMediaTypeNotAcceptableException}, que terminaba en un 500. El
 * corte 1 lo resolvía endpoint por endpoint fijando el {@code Content-Type}
 * en cada {@code ResponseEntity}; con una decena de endpoints nuevos eso es
 * una regla que alguien olvidará, así que se fija una sola vez aquí:
 * se ignora la cabecera {@code Accept} y el tipo por defecto es JSON.
 *
 * <p>Costo: ningún endpoint puede ofrecer otro formato según {@code Accept}.
 * Hoy no hace falta ninguno; si algún día hiciera falta (p. ej. exportar CSV),
 * se declara con {@code produces} en ese endpoint concreto.
 */
@Configuration
public class ConfiguracionContenido implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON);
    }
}
