package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin {@code FITO_CLAVE_FLOTA} la fábrica automática está apagada: la ruta no existe y responde el 404 uniforme. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroSinFlotaIntegracionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext contexto;

    @Test
    void sinClaveConfiguradaElControladorNoExisteYLaRutaDaElNotFoundUniforme() throws Exception {
        assertThat(contexto.getBeanNamesForType(RegistroDeDispositivoController.class)).isEmpty();

        mockMvc.perform(post("/api/v1/dispositivos/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serial\":\"fito-ABC123\",\"secreto\":\"" + "s".repeat(40) + "\",\"firma\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECURSO_NO_ENCONTRADO"));
    }
}
