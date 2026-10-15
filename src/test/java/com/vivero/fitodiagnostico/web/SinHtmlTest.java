package com.vivero.fitodiagnostico.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
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
 *
 * <p>Corte 2: con Spring Security delante, los 401 y 403 los escribe un filtro
 * antes de llegar a Spring MVC, así que ningún manejador de errores del
 * controlador los cubre; las pruebas de abajo confirman que también salen como
 * JSON con {@code Accept: text/html}, sin sesión, con una sesión de cliente en
 * una ruta de administrador y con un token inválido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SinHtmlTest {

    @LocalServerPort
    private int puerto;

    // El cliente por defecto (HttpURLConnection) lanza una excepción al recibir un 401 en un POST con
    // cuerpo ("cannot retry due to server authentication"), un artefacto del cliente y no de la API.
    // El cliente HTTP del JDK devuelve el 401 como cualquier otra respuesta.
    private final TestRestTemplate restTemplate =
            new TestRestTemplate(new RestTemplateBuilder().requestFactory(() -> new JdkClientHttpRequestFactory()));

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String cuerpo) {
        return pedirConToken(metodo, ruta, cuerpo, null);
    }

    private ResponseEntity<String> pedirConToken(HttpMethod metodo, String ruta, String cuerpo, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (cuerpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> entity = new HttpEntity<>(cuerpo, headers);
        return restTemplate.exchange("http://localhost:" + puerto + ruta, metodo, entity, String.class);
    }

    private void verificarNuncaHtml(HttpMethod metodo, String ruta, String cuerpo) {
        verificarNuncaHtml(pedir(metodo, ruta, cuerpo), metodo, ruta);
    }

    private void verificarNuncaHtml(ResponseEntity<String> respuesta, HttpMethod metodo, String ruta) {

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

    // ---- corte 2: seguridad ----

    /** Registra un cliente nuevo por la API real y devuelve su token. */
    private String tokenDeUnClienteNuevo() {
        String correo = "sinhtml-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity("http://localhost:" + puerto + "/api/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Sin Html\",\"correo\":\"" + correo + "\",\"contrasena\":\"clave-larga-1\"}", json),
                String.class);
        ResponseEntity<String> sesion = restTemplate.postForEntity("http://localhost:" + puerto + "/api/v1/auth/sesion",
                new HttpEntity<>("{\"correo\":\"" + correo + "\",\"contrasena\":\"clave-larga-1\"}", json),
                String.class);
        return com.jayway.jsonpath.JsonPath.read(sesion.getBody(), "$.token");
    }

    @Test
    void sinTokenUnaRutaProtegidaDa401EnJsonAunqueSePidaHtml() {
        ResponseEntity<String> respuesta = pedir(HttpMethod.GET, "/api/v1/cuenta", null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verificarNuncaHtml(respuesta, HttpMethod.GET, "/api/v1/cuenta");
        assertThat(respuesta.getBody()).contains("\"error\":\"NO_AUTENTICADO\"");
    }

    @Test
    void conUnTokenInvalidoDa401EnJson() {
        ResponseEntity<String> respuesta = pedirConToken(HttpMethod.GET, "/api/v1/cuenta", null, "esto.no.es.un.jwt");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verificarNuncaHtml(respuesta, HttpMethod.GET, "/api/v1/cuenta");
    }

    @Test
    void unClienteEnUnaRutaDeAdministracionDa403EnJson() {
        String token = tokenDeUnClienteNuevo();

        for (String ruta : new String[] {"/api/v1/admin/usuarios", "/api/v1/admin/especies"}) {
            ResponseEntity<String> respuesta = pedirConToken(HttpMethod.GET, ruta, null, token);

            assertThat(respuesta.getStatusCode()).as(ruta).isEqualTo(HttpStatus.FORBIDDEN);
            verificarNuncaHtml(respuesta, HttpMethod.GET, ruta);
            assertThat(respuesta.getBody()).contains("\"error\":\"ACCESO_DENEGADO\"");
        }
    }

    @Test
    void lasRutasAutenticadasConSesionValidaTambienRespondenJsonConAcceptHtml() {
        String token = tokenDeUnClienteNuevo();

        ResponseEntity<String> respuesta = pedirConToken(HttpMethod.GET, "/api/v1/cuenta", null, token);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verificarNuncaHtml(respuesta, HttpMethod.GET, "/api/v1/cuenta");
    }

    @Test
    void elRegistroConCuerpoInvalidoYElLoginFallidoNuncaDevuelvenHtml() {
        verificarNuncaHtml(HttpMethod.POST, "/api/v1/auth/registro", "{\"nombre\": 1}");
        verificarNuncaHtml(HttpMethod.POST, "/api/v1/auth/sesion",
                "{\"correo\":\"nadie@vivero.co\",\"contrasena\":\"incorrecta-1\"}");
    }

    @Test
    void unaRutaInexistenteConSesionNuncaDevuelveHtml() {
        ResponseEntity<String> respuesta = pedirConToken(HttpMethod.GET, "/api/v1/no-existe", null, tokenDeUnClienteNuevo());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verificarNuncaHtml(respuesta, HttpMethod.GET, "/api/v1/no-existe");
    }

    // ---- corte 2, bloque 3: plantas ----

    @Test
    void lasRutasDePlantasNuncaDevuelvenHtmlConSesionNiSinElla() {
        String ruta = "/api/v1/plantas/" + java.util.UUID.randomUUID();
        verificarNuncaHtml(HttpMethod.GET, "/api/v1/plantas", null);   // 401 sin sesión

        String token = tokenDeUnClienteNuevo();
        for (String[] caso : new String[][] {
                {"GET", ruta}, {"GET", ruta + "/diagnostico"}, {"GET", ruta + "/diagnosticos"},
                {"GET", ruta + "/diagnosticos?pagina=-1"}, {"GET", "/api/v1/plantas/no-es-uuid"},
                {"DELETE", ruta}, {"POST", ruta + "/mediciones"}, {"POST", "/api/v1/plantas"}}) {
            ResponseEntity<String> respuesta = pedirConToken(HttpMethod.valueOf(caso[0]), caso[1],
                    caso[0].equals("POST") ? "{\"humedad\": \"x\"}" : null, token);
            verificarNuncaHtml(respuesta, HttpMethod.valueOf(caso[0]), caso[1]);
        }
    }

    @Test
    void unaPlantaInexistenteDa404EnJsonYElDiagnosticoSueltoSigueSiendoPublico() {
        ResponseEntity<String> respuesta = pedirConToken(HttpMethod.GET,
                "/api/v1/plantas/" + java.util.UUID.randomUUID(), null, tokenDeUnClienteNuevo());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).contains("\"error\":\"PLANTA_NO_ENCONTRADA\"");
        // D6: sin token, el endpoint suelto sigue respondiendo.
        assertThat(pedir(HttpMethod.POST, "/api/v1/diagnosticos",
                "{\"especie\":\"potos\",\"humedad\":50,\"luz\":700,\"temperatura\":22}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---- corte 2, bloque 4: 415, compartir, recuperación, solicitudes y el endpoint del WebSocket ----

    private ResponseEntity<String> pedirConTipo(HttpMethod metodo, String ruta, String cuerpo, MediaType tipo, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        headers.setContentType(tipo);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange("http://localhost:" + puerto + ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    @Test
    void unTipoDeContenidoNoSoportadoDa415EnJsonYNoUn500() {
        // Ruta pública y ruta autenticada; antes del bloque 4 las dos respondían 500.
        ResponseEntity<String> publica = pedirConTipo(HttpMethod.POST, "/api/v1/diagnosticos", "hola", MediaType.TEXT_PLAIN, null);
        ResponseEntity<String> autenticada = pedirConTipo(HttpMethod.POST, "/api/v1/plantas", "<planta/>",
                MediaType.APPLICATION_XML, tokenDeUnClienteNuevo());

        for (ResponseEntity<String> respuesta : java.util.List.of(publica, autenticada)) {
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            assertThat(respuesta.getBody()).contains("\"error\":\"TIPO_DE_CONTENIDO_NO_SOPORTADO\"");
        }
    }

    @Test
    void lasRutasNuevasNuncaDevuelvenHtml() {
        String planta = "/api/v1/plantas/" + java.util.UUID.randomUUID();
        verificarNuncaHtml(HttpMethod.POST, "/api/v1/auth/recuperacion", "{\"correo\":\"nadie@vivero.co\"}");
        verificarNuncaHtml(HttpMethod.POST, "/api/v1/auth/restablecimiento", "{\"token\":\"x\",\"contrasenaNueva\":\"clave-larga-1\"}");
        verificarNuncaHtml(HttpMethod.POST, "/api/v1/invitaciones/aceptar-con-registro",
                "{\"token\":\"x\",\"nombre\":\"N\",\"correo\":\"n@vivero.co\",\"contrasena\":\"clave-larga-1\"}");
        verificarNuncaHtml(HttpMethod.GET, "/api/v1/solicitudes-especie", null);   // 401 sin sesión
        verificarNuncaHtml(HttpMethod.GET, "/api/v1/admin/solicitudes-especie", null);

        String token = tokenDeUnClienteNuevo();
        for (String[] caso : new String[][] {
                {"POST", planta + "/invitaciones"}, {"GET", planta + "/invitaciones"}, {"GET", planta + "/invitados"},
                {"DELETE", planta + "/invitados/" + java.util.UUID.randomUUID()},
                {"DELETE", planta + "/invitaciones/" + java.util.UUID.randomUUID()},
                {"GET", "/api/v1/plantas/compartidas"}, {"DELETE", planta.replace("/plantas/", "/plantas/compartidas/")},
                {"POST", "/api/v1/invitaciones/aceptar"}, {"POST", "/api/v1/solicitudes-especie"},
                {"GET", "/api/v1/admin/solicitudes-especie"}}) {
            ResponseEntity<String> respuesta = pedirConToken(HttpMethod.valueOf(caso[0]), caso[1],
                    caso[0].equals("POST") ? "{\"token\":\"inexistente\"}" : null, token);
            verificarNuncaHtml(respuesta, HttpMethod.valueOf(caso[0]), caso[1]);
        }
    }

    @Test
    void elEndpointDelWebSocketConUnGetNormalResponde400EnJsonNoEnTextoPlano() {
        ResponseEntity<String> respuesta = pedir(HttpMethod.GET, "/ws", null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verificarNuncaHtml(respuesta, HttpMethod.GET, "/ws");
        assertThat(respuesta.getBody()).contains("\"error\"");
    }
}
