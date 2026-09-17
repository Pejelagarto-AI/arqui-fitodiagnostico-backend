package com.vivero.fitodiagnostico.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RA1: el backend responde exclusivamente JSON. Ninguna plantilla ni HTML
 * construido en el controlador, ni siquiera cuando el cliente lo pide
 * explícitamente con {@code Accept: text/html} o cuando la ruta cae fuera de
 * {@code /api} (la consola de H2, la raíz, una ruta inexistente). Se usa
 * {@code webEnvironment = RANDOM_PORT} porque {@code /error} sólo lo dispara
 * el contenedor servlet real cuando la excepción escapa del dispatcher de
 * Spring MVC — MockMvc no lo reproduce.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SinHtmlTest {

    @LocalServerPort
    private int puerto;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        if (cuerpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> entity = new HttpEntity<>(cuerpo, headers);
        return restTemplate.exchange("http://localhost:" + puerto + ruta, metodo, entity, String.class);
    }

    private void verificarNuncaHtml(HttpMethod metodo, String ruta, String cuerpo) {
        ResponseEntity<String> respuesta = pedir(metodo, ruta, cuerpo);

        MediaType tipo = respuesta.getHeaders().getContentType();
        assertThat(tipo)
                .as("Content-Type de %s %s con Accept: text/html", metodo, ruta)
                .isNotNull();
        assertThat(tipo.isCompatibleWith(MediaType.TEXT_HTML))
                .as("Content-Type de %s %s no debería ser HTML pero fue %s", metodo, ruta, tipo)
                .isFalse();
        assertThat(tipo.isCompatibleWith(MediaType.APPLICATION_JSON))
                .as("Content-Type de %s %s debería ser JSON pero fue %s", metodo, ruta, tipo)
                .isTrue();
    }

    @Test
    void raizNuncaDevuelveHtml() {
        verificarNuncaHtml(HttpMethod.GET, "/", null);
    }

    @Test
    void rutaInexistenteNuncaDevuelveHtml() {
        verificarNuncaHtml(HttpMethod.GET, "/no-existe", null);
    }

    @Test
    void consolaH2SinBarraNuncaDevuelveHtml() {
        verificarNuncaHtml(HttpMethod.GET, "/h2-console", null);
    }

    @Test
    void consolaH2ConBarraNuncaDevuelveHtml() {
        verificarNuncaHtml(HttpMethod.GET, "/h2-console/", null);
    }

    @Test
    void metodoNoPermitidoEnDiagnosticosNuncaDevuelveHtml() {
        verificarNuncaHtml(HttpMethod.GET, "/api/v1/diagnosticos", null);
    }

    @Test
    void cuerpoInvalidoEnDiagnosticosNuncaDevuelveHtml() {
        verificarNuncaHtml(HttpMethod.POST, "/api/v1/diagnosticos", "{\"especie\": 123}");
    }

    @Test
    void especiesNuncaDevuelveHtmlYSigueRespondiendo200() {
        ResponseEntity<String> respuesta = pedir(HttpMethod.GET, "/api/v1/especies", null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        MediaType tipo = respuesta.getHeaders().getContentType();
        assertThat(tipo).isNotNull();
        assertThat(tipo.isCompatibleWith(MediaType.TEXT_HTML)).isFalse();
    }
}
