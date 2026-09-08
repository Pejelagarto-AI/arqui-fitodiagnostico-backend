package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;
import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web (sección 11): sólo el controlador y el manejador de errores,
 * con el caso de uso simulado. Cubre validación, códigos de error y la forma
 * exacta del contrato de la sección 2.
 */
@WebMvcTest(DiagnosticoController.class)
class DiagnosticoControllerTest {

    private static final String RUTA = "/api/v1/diagnosticos";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioDiagnostico servicio;

    private static Especie monstera() {
        return new Especie(
                "Monstera deliciosa", "costilla de Adán",
                new Rango(18.0, 29.0), new Rango(55.0, 80.0), new Rango(1000, 2500));
    }

    @Test
    void devuelve200ConLaFormaExactaDelContratoYCabeceraDeCache() throws Exception {
        Especie especie = monstera();
        Ambiente ambiente = new Ambiente(19.4, 42.0, 850);
        Diagnostico diagnostico = new Diagnostico(
                especie, ambiente, "NECESITA_AGUA",
                "humedad relativa 42.0 % por debajo del mínimo 55.0 %",
                Instant.parse("2026-09-08T14:22:03Z"));

        when(servicio.diagnosticar(anyString(), any(Ambiente.class))).thenReturn(diagnostico);

        mockMvc.perform(get(RUTA)
                        .param("especie", "Monstera deliciosa")
                        .param("temperaturaC", "19.4")
                        .param("humedadRelativa", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.especie.nombreCientifico").value("Monstera deliciosa"))
                .andExpect(jsonPath("$.especie.nombreComun").value("costilla de Adán"))
                .andExpect(jsonPath("$.lectura.temperaturaC").value(19.4))
                .andExpect(jsonPath("$.lectura.humedadRelativa").value(42.0))
                .andExpect(jsonPath("$.lectura.luzLux").value(850))
                .andExpect(jsonPath("$.estado").value("NECESITA_AGUA"))
                .andExpect(jsonPath("$.detalle").value("humedad relativa 42.0 % por debajo del mínimo 55.0 %"))
                .andExpect(jsonPath("$.evaluadoEn").value("2026-09-08T14:22:03Z"));
    }

    @Test
    void devuelve400CuandoFaltaUnParametro() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("especie", "Monstera deliciosa")
                        .param("temperaturaC", "19.4")
                        .param("humedadRelativa", "42.0"))
                // falta luzLux
                .andExpect(status().isBadRequest());
    }

    @Test
    void devuelve400CuandoUnParametroVieneFueraDeRango() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("especie", "Monstera deliciosa")
                        .param("temperaturaC", "999.0") // fuera de -20.0..60.0
                        .param("humedadRelativa", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void devuelve404CuandoLaEspecieNoExiste() throws Exception {
        when(servicio.diagnosticar(anyString(), any(Ambiente.class)))
                .thenThrow(new EspecieNoEncontradaException("Especie inexistens"));

        mockMvc.perform(get(RUTA)
                        .param("especie", "Especie inexistens")
                        .param("temperaturaC", "19.4")
                        .param("humedadRelativa", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isNotFound());
    }

    @Test
    void devuelve422CuandoLaLecturaEsFisicamenteImposible() throws Exception {
        when(servicio.diagnosticar(anyString(), any(Ambiente.class)))
                .thenThrow(new LecturaInvalidaException("humedad relativa -5.0 % fuera del rango físico del sensor"));

        mockMvc.perform(get(RUTA)
                        .param("especie", "Monstera deliciosa")
                        .param("temperaturaC", "19.4")
                        .param("humedadRelativa", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isUnprocessableEntity());
    }
}
