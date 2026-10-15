package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import com.vivero.fitodiagnostico.aplicacion.ServicioPlantas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un {@code Content-Type} que no es JSON responde 415 con el cuerpo uniforme, no
 * 500 (límite del bloque 1 resuelto, D60). Se prueba en una ruta pública y en una
 * autenticada, con varios tipos, y que ninguna llega al servicio.
 */
@WebMvcTest({DiagnosticoController.class, PlantasController.class})
@Import(SeguridadDePruebas.class)
class TipoDeContenidoWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ServicioDiagnostico diagnostico;
    @MockBean private ServicioPlantas plantas;

    @Test
    void unaRutaPublicaConTextoPlanoDa415EnJsonUniforme() throws Exception {
        mockMvc.perform(post("/api/v1/diagnosticos").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("TIPO_DE_CONTENIDO_NO_SOPORTADO"))
                .andExpect(jsonPath("$.mensaje").value("el cuerpo debe enviarse como application/json."))
                .andExpect(jsonPath("$.detalle").isEmpty());
        verifyNoInteractions(diagnostico);
    }

    @Test
    void unaRutaAutenticadaConXmlFormularioOSinTipoTambienDa415() throws Exception {
        for (MediaType tipo : new MediaType[]{MediaType.APPLICATION_XML, MediaType.APPLICATION_FORM_URLENCODED,
                MediaType.TEXT_HTML}) {
            mockMvc.perform(post("/api/v1/plantas").with(jwt().jwt(j -> j.subject("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10"))
                            .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                            .contentType(tipo).content("<planta/>"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("TIPO_DE_CONTENIDO_NO_SOPORTADO"));
        }
        verifyNoInteractions(plantas);
    }

    @Test
    void elJsonConCharsetSigueFuncionando() throws Exception {
        mockMvc.perform(post("/api/v1/plantas").with(jwt().jwt(j -> j.subject("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                        .contentType("application/json;charset=UTF-8").content("{}"))
                .andExpect(status().isBadRequest())   // llegó a la validación: el tipo se aceptó
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
    }
}
