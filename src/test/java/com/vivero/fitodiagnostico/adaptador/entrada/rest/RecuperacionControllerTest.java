package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.aplicacion.ServicioRecuperacion;
import com.vivero.fitodiagnostico.dominio.excepcion.RestablecimientoNoVigenteException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rebanada web de la recuperación de contraseña (RF-A7, RF-A8): públicas, sin enumeración, 410 uniforme. */
@WebMvcTest(RecuperacionController.class)
@Import(SeguridadDePruebas.class)
class RecuperacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioRecuperacion servicio;

    @Test
    void solicitarResponde202ConUnMensajeQueNoConfirmaNada() throws Exception {
        mockMvc.perform(post("/api/v1/auth/recuperacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@vivero.co\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje").value(
                        "Si el correo está registrado, te enviamos un enlace para restablecer la contraseña."));
        verify(servicio).solicitar("ana@vivero.co");
    }

    @Test
    void laRespuestaEsLaMismaConUnCorreoQueNoExiste() throws Exception {
        // El servicio no lanza ni devuelve nada distinto: el controlador no tiene cómo diferenciar.
        String existente = mockMvc.perform(post("/api/v1/auth/recuperacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@vivero.co\"}")).andReturn().getResponse().getContentAsString();
        String inexistente = mockMvc.perform(post("/api/v1/auth/recuperacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"nadie@vivero.co\"}")).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(inexistente).isEqualTo(existente);
    }

    @Test
    void solicitarValidaLaFormaDelCorreoEnElBorde() throws Exception {
        for (String cuerpo : new String[]{"{}", "{\"correo\":\"\"}", "{\"correo\":\"no-es-correo\"}", "{\"correo\":"}) {
            mockMvc.perform(post("/api/v1/auth/recuperacion").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void restablecerDa204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/restablecimiento").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok-1\",\"contrasenaNueva\":\"clave-nueva-1\"}"))
                .andExpect(status().isNoContent());
        verify(servicio).restablecer("tok-1", "clave-nueva-1");
    }

    @Test
    void restablecerValidaLaContrasenaNuevaConLosLimitesDelRegistro() throws Exception {
        for (String cuerpo : new String[]{
                "{\"token\":\"t\",\"contrasenaNueva\":\"corta\"}",
                "{\"token\":\"t\",\"contrasenaNueva\":\"" + "x".repeat(73) + "\"}",
                "{\"contrasenaNueva\":\"clave-nueva-1\"}",
                "{\"token\":\" \",\"contrasenaNueva\":\"clave-nueva-1\"}"}) {
            mockMvc.perform(post("/api/v1/auth/restablecimiento").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void unEnlaceUsadoCaducadoOInvalidadoDa410ConUnSoloCodigo() throws Exception {
        doThrow(new RestablecimientoNoVigenteException()).when(servicio).restablecer(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/restablecimiento").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok-1\",\"contrasenaNueva\":\"clave-nueva-1\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("RESTABLECIMIENTO_NO_VIGENTE"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }
}
