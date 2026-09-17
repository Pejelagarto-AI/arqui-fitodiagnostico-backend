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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web (sección 11): sólo el controlador y el manejador de errores,
 * con el caso de uso simulado. Cubre la forma exacta del contrato de la
 * sección 2 (POST) y el cuerpo de error uniforme para cada caso de la
 * sección de errores.
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
    void devuelve200ConLaFormaExactaDelContrato() throws Exception {
        Especie especie = sansevieria();
        // Todos OPTIMO: humedad 32.5 (20-45), luz 850 (200-1500), temperatura 21.0 (15-29).
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.HUMEDAD, 32.5),
                new Lectura(Magnitud.LUZ, 850),
                new Lectura(Magnitud.TEMPERATURA, 21.0));
        var parametros = new ClasificadorDeParametros().clasificar(especie, medicion);
        Diagnostico diagnostico = new Diagnostico(
                especie, medicion, EstadoGlobal.EN_RIESGO,
                parametros,
                new RedactorDeRecomendaciones().redactar(parametros),
                Instant.parse("2026-09-08T14:22:03Z"));

        when(servicio.diagnosticar(anyString(), any(Medicion.class))).thenReturn(diagnostico);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"especie":"sansevieria","humedad":32.5,"luz":850,"temperatura":21.0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especie").value("sansevieria"))
                .andExpect(jsonPath("$.estado").value("EN_RIESGO"))
                .andExpect(jsonPath("$.parametros.length()").value(3))
                .andExpect(jsonPath("$.parametros[0].nombre").value("humedad"))
                .andExpect(jsonPath("$.parametros[0].valor").value(32.5))
                .andExpect(jsonPath("$.parametros[0].unidad").value("%"))
                .andExpect(jsonPath("$.parametros[0].rangoOptimo[0]").value(20.0))
                .andExpect(jsonPath("$.parametros[0].rangoOptimo[1]").value(45.0))
                .andExpect(jsonPath("$.parametros[0].estado").value("OPTIMO"))
                .andExpect(jsonPath("$.parametros[1].nombre").value("luz"))
                .andExpect(jsonPath("$.parametros[1].valor").value(850.0))
                .andExpect(jsonPath("$.parametros[1].unidad").value("lux"))
                .andExpect(jsonPath("$.parametros[1].estado").value("OPTIMO"))
                .andExpect(jsonPath("$.parametros[2].nombre").value("temperatura"))
                .andExpect(jsonPath("$.parametros[2].valor").value(21.0))
                .andExpect(jsonPath("$.parametros[2].unidad").value("°C"))
                .andExpect(jsonPath("$.parametros[2].estado").value("OPTIMO"))
                .andExpect(jsonPath("$.recomendaciones.length()").value(0))
                .andExpect(jsonPath("$.lectura").doesNotExist())
                .andExpect(jsonPath("$.evaluadoEn").doesNotExist());
    }

    @Test
    void devuelve404ConCuerpoUniformeCuandoLaEspecieNoExiste() throws Exception {
        when(servicio.diagnosticar(anyString(), any(Medicion.class)))
                .thenThrow(new EspecieNoEncontradaException("especie inexistente"));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"especie":"especie inexistente","humedad":32.5,"luz":850,"temperatura":21.0}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ESPECIE_NO_SOPORTADA"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalle.especie").value("especie inexistente"));
    }

    @Test
    void devuelve400ConCuerpoUniformeCuandoFaltaUnParametro() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"especie":"sansevieria","luz":850,"temperatura":21.0}"""))
                // falta humedad
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalle.campo").value("humedad"));
    }

    @Test
    void devuelve400CuandoLaEspecieVieneEnBlanco() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"especie":"","humedad":32.5,"luz":850,"temperatura":21.0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("especie"));
    }

    @Test
    void devuelve400ConDetalleDelCampoCuandoElValorNoEsNumerico() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"especie":"sansevieria","humedad":"mucha","luz":850,"temperatura":21.0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalle.campo").value("humedad"));
    }

    @Test
    void devuelve400ConDetalleDeLaMagnitudCuandoElValorEsFisicamenteImposible() throws Exception {
        when(servicio.diagnosticar(anyString(), any(Medicion.class)))
                .thenThrow(new LecturaInvalidaException(Magnitud.HUMEDAD,
                        "humedad del sustrato -5.0 % fuera del rango físico del sensor"));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"especie":"sansevieria","humedad":-5.0,"luz":850,"temperatura":21.0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalle.campo").value("humedad"));
    }

    @Test
    void devuelve400ConDetalleVacioCuandoElCuerpoNoEsJsonValido() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalle").isEmpty());
    }

    @Test
    void devuelve405ConCuerpoUniformeParaGet() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METODO_NO_PERMITIDO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }
}
