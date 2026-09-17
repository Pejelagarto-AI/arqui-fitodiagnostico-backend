package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,OPTIONS"));
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
