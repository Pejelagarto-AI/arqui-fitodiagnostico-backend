package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioConsultaDeDiagnosticos;
import com.vivero.fitodiagnostico.aplicacion.ServicioMediciones;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web de las mediciones manuales (RF-F1) y de las consultas del
 * diagnóstico de una planta (RF-F4, RF-F5).
 */
@WebMvcTest({MedicionesController.class, DiagnosticosDePlantaController.class})
@Import(SeguridadDePruebas.class)
class MedicionesYDiagnosticosDePlantaControllerTest {

    private static final UUID YO = UUID.fromString("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10");
    private static final UUID PLANTA = UUID.fromString("0f6c1d3e-5b0a-4c55-8a53-1c1d2d3e4f50");
    private static final String CUERPO = "{\"humedad\":28,\"luz\":700,\"temperatura\":22}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ServicioMediciones mediciones;
    @MockBean private ServicioConsultaDeDiagnosticos consulta;

    private static RequestPostProcessor conSesion() {
        return jwt().jwt(j -> j.subject(YO.toString())).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static DiagnosticoDePlanta registro(Medicion medicion, OrigenDeMedicion origen) {
        Diagnostico diagnostico = new EvaluadorDeEstado(new ClasificadorDeParametros(),
                new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO), new RedactorDeRecomendaciones())
                .evaluar(Muestras.especie("helecho"), medicion);
        return new DiagnosticoDePlanta(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), PLANTA, origen,
                Instant.parse("2026-10-01T12:00:00Z"), diagnostico);
    }

    // ---- POST /plantas/{id}/mediciones ----

    @Test
    void registrarUnaMedicionManualDa201ConElDiagnosticoYSuOrigen() throws Exception {
        when(mediciones.registrarManual(eq(YO), eq(PLANTA), any(Medicion.class)))
                .thenReturn(registro(Muestras.medicionEnRiesgo(), OrigenDeMedicion.MANUAL));

        mockMvc.perform(post("/api/v1/plantas/{id}/mediciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plantaId").value(PLANTA.toString()))
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.registradoEn").value("2026-10-01T12:00:00Z"))
                // El mismo cuerpo del contrato del Anexo A dentro de "diagnostico".
                .andExpect(jsonPath("$.diagnostico.especie").value("helecho"))
                .andExpect(jsonPath("$.diagnostico.estado").value("EN_RIESGO"))
                .andExpect(jsonPath("$.diagnostico.parametros.length()").value(3))
                .andExpect(jsonPath("$.diagnostico.parametros[0].nombre").value("humedad"))
                .andExpect(jsonPath("$.diagnostico.recomendaciones.length()").value(1));
    }

    @Test
    void laMedicionLlegaAlServicioConvertidaADominioEnElOrdenDelContrato() throws Exception {
        when(mediciones.registrarManual(any(), any(), any()))
                .thenReturn(registro(Muestras.medicionEnRiesgo(), OrigenDeMedicion.MANUAL));

        mockMvc.perform(post("/api/v1/plantas/{id}/mediciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isCreated());

        ArgumentCaptor<Medicion> medicion = ArgumentCaptor.forClass(Medicion.class);
        verify(mediciones).registrarManual(eq(YO), eq(PLANTA), medicion.capture());
        assertThat(medicion.getValue()).isEqualTo(Muestras.medicion(28, 700, 22));
    }

    @Test
    void unCampoAusenteNuloOConLecturaImposibleDa400SinLlamarAlServicio() throws Exception {
        String[] cuerpos = {
            "{\"luz\":700,\"temperatura\":22}",                       // sin humedad
            "{\"humedad\":null,\"luz\":700,\"temperatura\":22}",
            "{\"humedad\":\"mucha\",\"luz\":700,\"temperatura\":22}", // no numérico
            "{\"humedad\":-5,\"luz\":700,\"temperatura\":22}",        // físicamente imposible
            "{\"humedad\":28,\"luz\":999999,\"temperatura\":22}",
        };
        for (String cuerpo : cuerpos) {
            mockMvc.perform(post("/api/v1/plantas/{id}/mediciones", PLANTA).with(conSesion())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        }
        verifyNoInteractions(mediciones);
    }

    @Test
    void unaLecturaImposibleNombraLaMagnitud() throws Exception {
        mockMvc.perform(post("/api/v1/plantas/{id}/mediciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"humedad\":-5,\"luz\":700,\"temperatura\":22}"))
                .andExpect(jsonPath("$.detalle.campo").value("humedad"));
    }

    @Test
    void elInvitadoRecibe403ElAjeno404() throws Exception {
        when(mediciones.registrarManual(any(), any(), any()))
                .thenThrow(new OperacionNoPermitidaException("solo el dueño de la planta puede hacer esta operación"))
                .thenThrow(new PlantaNoEncontradaException(PLANTA));

        mockMvc.perform(post("/api/v1/plantas/{id}/mediciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        mockMvc.perform(post("/api/v1/plantas/{id}/mediciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLANTA_NO_ENCONTRADA"));
    }

    // ---- GET /plantas/{id}/diagnostico ----

    @Test
    void elDiagnosticoVigenteDevuelveHayDiagnosticoYElRegistro() throws Exception {
        when(consulta.vigente(YO, PLANTA)).thenReturn(Optional.of(registro(Muestras.medicionSaludable(), OrigenDeMedicion.MANUAL)));

        mockMvc.perform(get("/api/v1/plantas/{id}/diagnostico", PLANTA).with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayDiagnostico").value(true))
                .andExpect(jsonPath("$.mensaje").doesNotExist())
                .andExpect(jsonPath("$.diagnostico.diagnostico.estado").value("SALUDABLE"));
    }

    @Test
    void unaPlantaSinDiagnosticoDa200YNoErrorConHayDiagnosticoEnFalso() throws Exception {
        // RN9.
        when(consulta.vigente(YO, PLANTA)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/plantas/{id}/diagnostico", PLANTA).with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayDiagnostico").value(false))
                .andExpect(jsonPath("$.diagnostico").value((Object) null))
                .andExpect(jsonPath("$.mensaje").isString());
    }

    @Test
    void elDiagnosticoDeUnaPlantaAjenaDa404() throws Exception {
        when(consulta.vigente(YO, PLANTA)).thenThrow(new PlantaNoEncontradaException(PLANTA));

        mockMvc.perform(get("/api/v1/plantas/{id}/diagnostico", PLANTA).with(conSesion()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLANTA_NO_ENCONTRADA"));
    }

    // ---- GET /plantas/{id}/diagnosticos ----

    @Test
    void elHistorialUsaLaPrimeraPaginaDe20PorDefectoYDevuelveElTotal() throws Exception {
        when(consulta.historial(eq(YO), eq(PLANTA), any(Paginacion.class))).thenReturn(new Pagina<>(
                List.of(registro(Muestras.medicionCritica(), OrigenDeMedicion.DISPOSITIVO)), 0, 20, 41));

        mockMvc.perform(get("/api/v1/plantas/{id}/diagnosticos", PLANTA).with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].origen").value("DISPOSITIVO"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.total").value(41))
                .andExpect(jsonPath("$.totalDePaginas").value(3));
        verify(consulta).historial(YO, PLANTA, new Paginacion(0, 20));
    }

    @Test
    void elHistorialPasaLosParametrosDePaginacion() throws Exception {
        when(consulta.historial(any(), any(), any())).thenReturn(new Pagina<>(List.of(), 2, 5, 3));

        mockMvc.perform(get("/api/v1/plantas/{id}/diagnosticos", PLANTA).with(conSesion())
                        .param("pagina", "2").param("tamano", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));
        verify(consulta).historial(YO, PLANTA, new Paginacion(2, 5));
    }

    @Test
    void unaPaginacionInvalidaDa400() throws Exception {
        for (String[] par : new String[][] {{"pagina", "-1"}, {"tamano", "0"}, {"tamano", "101"}, {"pagina", "abc"}}) {
            mockMvc.perform(get("/api/v1/plantas/{id}/diagnosticos", PLANTA).with(conSesion()).param(par[0], par[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        }
        verifyNoInteractions(consulta);
    }

    @Test
    void elHistorialDeUnaPlantaAjenaDa404() throws Exception {
        when(consulta.historial(any(), any(), any())).thenThrow(new PlantaNoEncontradaException(PLANTA));

        mockMvc.perform(get("/api/v1/plantas/{id}/diagnosticos", PLANTA).with(conSesion()))
                .andExpect(status().isNotFound());
    }

    @Test
    void lasConsultasSoloAdmitenGetPorqueConsultarNuncaEscribe() throws Exception {
        // D3: no hay forma de escribir por las rutas de consulta.
        mockMvc.perform(post("/api/v1/plantas/{id}/diagnostico", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/plantas/{id}/diagnosticos", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(mediciones);
    }
}
