package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.aplicacion.ServicioPlantas;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieInactivaException;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web de las plantas (RF-D1 a RF-D5): forma de peticiones y
 * respuestas, validación en el borde y traducción de cada excepción. Quién
 * puede qué (dueño, invitado, ajeno) lo decide la aplicación y se prueba con
 * el contexto completo en {@code FlujoDePlantasIntegracionTest}.
 */
@WebMvcTest(PlantasController.class)
@Import(SeguridadDePruebas.class)
class PlantasControllerTest {

    private static final UUID YO = UUID.fromString("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10");
    private static final UUID PLANTA = UUID.fromString("0f6c1d3e-5b0a-4c55-8a53-1c1d2d3e4f50");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioPlantas servicio;

    private static RequestPostProcessor conSesion() {
        return jwt().jwt(j -> j.subject(YO.toString())).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static Planta pepa(UUID dueno) {
        return new Planta(PLANTA, dueno, "Pepa", Muestras.especie("helecho"), Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    void registrarDevuelve201ConLocationYLaPlantaConElIdDelTokenComoDueno() throws Exception {
        when(servicio.registrar(YO, "Pepa", "helecho")).thenReturn(pepa(YO));

        mockMvc.perform(post("/api/v1/plantas").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apodo\":\"Pepa\",\"especie\":\"helecho\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/plantas/" + PLANTA))
                .andExpect(jsonPath("$.id").value(PLANTA.toString()))
                .andExpect(jsonPath("$.duenoId").value(YO.toString()))
                .andExpect(jsonPath("$.apodo").value("Pepa"))
                .andExpect(jsonPath("$.especie").value("helecho"))
                .andExpect(jsonPath("$.registradaEn").value("2026-10-01T12:00:00Z"))
                .andExpect(jsonPath("$.esDueno").value(true));
    }

    @Test
    void elDuenoNuncaSeLeeDelCuerpo() throws Exception {
        // Un cliente malicioso que manda "duenoId" ajeno: el campo se ignora, manda el token.
        when(servicio.registrar(YO, "Pepa", "helecho")).thenReturn(pepa(YO));

        mockMvc.perform(post("/api/v1/plantas").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apodo\":\"Pepa\",\"especie\":\"helecho\",\"duenoId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
        verify(servicio).registrar(YO, "Pepa", "helecho");
    }

    @Test
    void registrarValidaLaFormaEnElBordeSinLlamarAlServicio() throws Exception {
        String[] cuerpos = {
            "{\"especie\":\"helecho\"}",                                              // sin apodo
            "{\"apodo\":\"  \",\"especie\":\"helecho\"}",                            // apodo en blanco
            "{\"apodo\":\"" + "x".repeat(61) + "\",\"especie\":\"helecho\"}",         // apodo largo
            "{\"apodo\":\"Pepa\"}",                                                   // sin especie
            "{\"apodo\":\"Pepa\",\"especie\":\"\"}",
            "{\"apodo\":",                                                            // JSON roto
        };
        for (String cuerpo : cuerpos) {
            mockMvc.perform(post("/api/v1/plantas").with(conSesion()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void elCampoReportadoEsElApodoAntesQueLaEspecie() throws Exception {
        mockMvc.perform(post("/api/v1/plantas").with(conSesion()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("apodo"));
    }

    @Test
    void registrarConEspecieInexistenteDa404YConDescontinuadaDa409() throws Exception {
        when(servicio.registrar(any(), anyString(), anyString()))
                .thenThrow(new EspecieNoEncontradaException("cactus"))
                .thenThrow(new EspecieInactivaException("lavanda"));

        mockMvc.perform(post("/api/v1/plantas").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apodo\":\"Pepa\",\"especie\":\"cactus\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ESPECIE_NO_SOPORTADA"));
        mockMvc.perform(post("/api/v1/plantas").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apodo\":\"Pepa\",\"especie\":\"lavanda\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESPECIE_DESCONTINUADA"))
                .andExpect(jsonPath("$.detalle.especie").value("lavanda"));
    }

    @Test
    void listarDevuelveSoloLoQueElServicioDaParaElTokenYMarcaEsDueno() throws Exception {
        when(servicio.listarPropias(YO)).thenReturn(List.of(pepa(YO)));

        mockMvc.perform(get("/api/v1/plantas").with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].apodo").value("Pepa"));
        verify(servicio).listarPropias(YO);
    }

    @Test
    void listarSinPlantasDaListaVaciaNoError() throws Exception {
        when(servicio.listarPropias(YO)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/plantas").with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void elDetalleDeUnaPlantaAjenaMarcaEsDuenoEnFalso() throws Exception {
        when(servicio.detalle(YO, PLANTA)).thenReturn(pepa(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/plantas/{id}", PLANTA).with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDueno").value(false));
    }

    @Test
    void unaPlantaSinAccesoDa404ConElMismoCuerpoQueUnaInexistente() throws Exception {
        // RN3 + D35: el cuerpo no distingue "no existe" de "no es tuya" (la aplicación lanza lo mismo).
        when(servicio.detalle(YO, PLANTA)).thenThrow(new PlantaNoEncontradaException(PLANTA));

        mockMvc.perform(get("/api/v1/plantas/{id}", PLANTA).with(conSesion()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLANTA_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.detalle.id").value(PLANTA.toString()));
    }

    @Test
    void unIdQueNoEsUuidDa400() throws Exception {
        mockMvc.perform(get("/api/v1/plantas/no-es-un-uuid").with(conSesion()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("id"));
    }

    @Test
    void editarDevuelveLaPlantaEditada() throws Exception {
        when(servicio.editar(YO, PLANTA, "Pepita", "potos"))
                .thenReturn(new Planta(PLANTA, YO, "Pepita", Muestras.especie("potos"), Instant.parse("2026-10-01T12:00:00Z")));

        mockMvc.perform(put("/api/v1/plantas/{id}", PLANTA).with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apodo\":\"Pepita\",\"especie\":\"potos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apodo").value("Pepita"))
                .andExpect(jsonPath("$.especie").value("potos"));
    }

    @Test
    void unInvitadoQueIntentaEditarOEliminarRecibe403ConCodigoPropio() throws Exception {
        // RN5.
        when(servicio.editar(any(), any(), anyString(), anyString()))
                .thenThrow(new OperacionNoPermitidaException("solo el dueño de la planta puede hacer esta operación"));
        doThrow(new OperacionNoPermitidaException("solo el dueño de la planta puede hacer esta operación"))
                .when(servicio).eliminar(any(), any());

        mockMvc.perform(put("/api/v1/plantas/{id}", PLANTA).with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apodo\":\"Mia\",\"especie\":\"helecho\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        mockMvc.perform(delete("/api/v1/plantas/{id}", PLANTA).with(conSesion()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
    }

    @Test
    void eliminarDa204ConElIdDelToken() throws Exception {
        mockMvc.perform(delete("/api/v1/plantas/{id}", PLANTA).with(conSesion()))
                .andExpect(status().isNoContent());
        verify(servicio).eliminar(YO, PLANTA);
    }

    @Test
    void conAcceptHtmlLaRespuestaSigueSiendoJsonTambienEnErrores() throws Exception {
        // RA1.
        when(servicio.detalle(YO, PLANTA)).thenThrow(new PlantaNoEncontradaException(PLANTA));

        mockMvc.perform(get("/api/v1/plantas/{id}", PLANTA).with(conSesion()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("<html"))));
    }
}
