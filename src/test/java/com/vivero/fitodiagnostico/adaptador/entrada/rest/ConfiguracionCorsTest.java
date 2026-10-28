package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RA7: el front se sirve desde otro origen. Verifica el preflight OPTIONS
 * sin levantar la app completa —{@link ConfiguracionCors} es un
 * {@code WebMvcConfigurer}, que @WebMvcTest recoge automáticamente.
 */
@WebMvcTest(DiagnosticoController.class)
@Import(SeguridadDePruebas.class)
class ConfiguracionCorsTest {

    private static final String RUTA = "/api/v1/diagnosticos";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioDiagnostico servicio;

    @Test
    void preflightDesdeElOrigenDelFrontDevuelveElHeaderDeCors() throws Exception {
        mockMvc.perform(options(RUTA)
                        .header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"));
    }

    @Test
    void elPreflightPermiteLaCabeceraAuthorizationDelJwt() throws Exception {
        // Sin Authorization en Access-Control-Allow-Headers, el navegador bloquea
        // toda petición autenticada del front aunque el servidor la aceptaría.
        mockMvc.perform(options(RUTA)
                        .header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type, authorization"));
    }

    @Test
    void preflightDeUnMetodoQueExigeSesionNoNecesitaTokenYSigueFuncionando() throws Exception {
        // El preflight del navegador nunca lleva credenciales: si la seguridad lo
        // bloqueara con 401, ninguna ruta protegida sería alcanzable desde el front.
        mockMvc.perform(options(RUTA)
                        .header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }

    @Test
    void preflightDesdeUnOrigenNoPermitidoNoTraeElHeaderDeCors() throws Exception {
        mockMvc.perform(options(RUTA)
                        .header("Origin", "http://origen-no-permitido.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
