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
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
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

    // Anexo B: sansevieria, humedad 20-45 %, luz 200-1500 lux, temperatura 15-29 °C.
    private static Especie sansevieria() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(20.0, 45.0));
        rangos.put(Magnitud.LUZ, new Rango(200, 1500));
        rangos.put(Magnitud.TEMPERATURA, new Rango(15.0, 29.0));
        return new Especie("sansevieria", rangos);
    }

    @Test
    void devuelve200ConLaFormaExactaDelContratoYCabeceraDeCache() throws Exception {
        Especie especie = sansevieria();
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.TEMPERATURA, 35.0),
                new Lectura(Magnitud.HUMEDAD, 10.0),
                new Lectura(Magnitud.LUZ, 850));
        // Orden del enum Magnitud: HUMEDAD, LUZ, TEMPERATURA. Humedad (10 < 20)
        // queda BAJO, luz (850) queda OPTIMO, temperatura (35 > 29) queda ALTO.
        var parametros = new ClasificadorDeParametros().clasificar(especie, medicion);
        Diagnostico diagnostico = new Diagnostico(
                especie, medicion, EstadoGlobal.EN_RIESGO,
                parametros,
                new RedactorDeRecomendaciones().redactar(parametros),
                Instant.parse("2026-09-08T14:22:03Z"));

        when(servicio.diagnosticar(anyString(), any(Medicion.class))).thenReturn(diagnostico);

        mockMvc.perform(get(RUTA)
                        .param("especie", "sansevieria")
                        .param("temperaturaC", "35.0")
                        .param("humedad", "10.0")
                        .param("luzLux", "850"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.especie").value("sansevieria"))
                .andExpect(jsonPath("$.lectura.temperaturaC").value(35.0))
                .andExpect(jsonPath("$.lectura.humedadRelativa").value(10.0))
                .andExpect(jsonPath("$.lectura.luzLux").value(850))
                .andExpect(jsonPath("$.estado").value("EN_RIESGO"))
                .andExpect(jsonPath("$.parametros.length()").value(3))
                .andExpect(jsonPath("$.parametros[0].nombre").value("humedad"))
                .andExpect(jsonPath("$.parametros[0].valor").value(10.0))
                .andExpect(jsonPath("$.parametros[0].unidad").value("%"))
                .andExpect(jsonPath("$.parametros[0].rangoOptimo[0]").value(20.0))
                .andExpect(jsonPath("$.parametros[0].rangoOptimo[1]").value(45.0))
                .andExpect(jsonPath("$.parametros[0].estado").value("BAJO"))
                .andExpect(jsonPath("$.recomendaciones.length()").value(2))
                .andExpect(jsonPath("$.recomendaciones[0]")
                        .value("Humedad del sustrato 10 % por debajo del rango óptimo 20–45 %: regar moderadamente."))
                .andExpect(jsonPath("$.recomendaciones[1]")
                        .value("Temperatura 35 °C por encima del rango óptimo 15–29 °C: llevarla a un lugar más fresco y ventilado."))
                .andExpect(jsonPath("$.evaluadoEn").value("2026-09-08T14:22:03Z"));
    }

    @Test
    void devuelve400CuandoFaltaUnParametro() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("especie", "sansevieria")
                        .param("temperaturaC", "19.4")
                        .param("humedad", "42.0"))
                // falta luzLux
                .andExpect(status().isBadRequest());
    }

    @Test
    void devuelve400CuandoUnParametroVieneFueraDeRango() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("especie", "sansevieria")
                        .param("temperaturaC", "999.0") // fuera de -20.0..60.0
                        .param("humedad", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void devuelve404CuandoLaEspecieNoExiste() throws Exception {
        when(servicio.diagnosticar(anyString(), any(Medicion.class)))
                .thenThrow(new EspecieNoEncontradaException("especie inexistens"));

        mockMvc.perform(get(RUTA)
                        .param("especie", "especie inexistens")
                        .param("temperaturaC", "19.4")
                        .param("humedad", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isNotFound());
    }

    @Test
    void devuelve422CuandoLaLecturaEsFisicamenteImposible() throws Exception {
        when(servicio.diagnosticar(anyString(), any(Medicion.class)))
                .thenThrow(new LecturaInvalidaException("humedad relativa -5.0 % fuera del rango físico del sensor"));

        mockMvc.perform(get(RUTA)
                        .param("especie", "sansevieria")
                        .param("temperaturaC", "19.4")
                        .param("humedad", "42.0")
                        .param("luzLux", "850"))
                .andExpect(status().isUnprocessableEntity());
    }
}
