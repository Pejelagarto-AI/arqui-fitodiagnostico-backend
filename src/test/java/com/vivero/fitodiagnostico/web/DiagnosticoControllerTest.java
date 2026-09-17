package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.LecturaInvalidaException;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

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
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 29.0));
        rangos.put(Magnitud.HUMEDAD, new Rango(55.0, 80.0));
        rangos.put(Magnitud.LUZ, new Rango(1000, 2500));
        return new Especie("Monstera deliciosa", "costilla de Adán", rangos);
    }

    @Test
    void devuelve200ConLaFormaExactaDelContratoYCabeceraDeCache() throws Exception {
        Especie especie = monstera();
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.TEMPERATURA, 19.4),
                new Lectura(Magnitud.HUMEDAD, 42.0),
                new Lectura(Magnitud.LUZ, 850));
        Diagnostico diagnostico = new Diagnostico(
                especie, medicion, EstadoGlobal.EN_RIESGO,
                new ClasificadorDeParametros().clasificar(especie, medicion),
                Instant.parse("2026-09-08T14:22:03Z"));

        when(servicio.diagnosticar(anyString(), any(Medicion.class))).thenReturn(diagnostico);

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
                .andExpect(jsonPath("$.estado").value("EN_RIESGO"))
                .andExpect(jsonPath("$.parametros.length()").value(3))
                .andExpect(jsonPath("$.parametros[1].nombre").value("humedad"))
                .andExpect(jsonPath("$.parametros[1].valor").value(42.0))
                .andExpect(jsonPath("$.parametros[1].unidad").value("%"))
                .andExpect(jsonPath("$.parametros[1].rangoOptimo[0]").value(55.0))
                .andExpect(jsonPath("$.parametros[1].rangoOptimo[1]").value(80.0))
                .andExpect(jsonPath("$.parametros[1].estado").value("BAJO"))
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
        when(servicio.diagnosticar(anyString(), any(Medicion.class)))
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
        when(servicio.diagnosticar(anyString(), any(Medicion.class)))
                .thenThrow(new LecturaInvalidaException("humedad relativa -5.0 % fuera del rango físico del sensor"));

        mockMvc.perform(get(RUTA)
                        .param("especie", "Monstera deliciosa")
                        .param("temperaturaC", "19.4")
                        .param("humedadRelativa", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isUnprocessableEntity());
    }
}
