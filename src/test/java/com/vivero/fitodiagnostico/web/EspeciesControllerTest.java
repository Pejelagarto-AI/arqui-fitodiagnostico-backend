package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioCatalogo;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web (sección 11): sólo el controlador, con el caso de uso
 * simulado. Cubre la forma exacta de RF5: nombre, rangos con claves en
 * minúscula, orden y cabecera de caché.
 */
@WebMvcTest(EspeciesController.class)
class EspeciesControllerTest {

    private static final String RUTA = "/api/v1/especies";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioCatalogo servicio;

    private static Especie especie(String nombre, Rango humedad, Rango luz, Rango temperatura) {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, humedad);
        rangos.put(Magnitud.LUZ, luz);
        rangos.put(Magnitud.TEMPERATURA, temperatura);
        return new Especie(nombre, rangos);
    }

    @Test
    void devuelve200ConLasEspeciesEnOrdenYCabeceraDeCache() throws Exception {
        Especie helecho = especie("helecho",
                new Rango(60.0, 85.0), new Rango(150.0, 800.0), new Rango(16.0, 26.0));
        Especie lavanda = especie("lavanda",
                new Rango(25.0, 50.0), new Rango(1000.0, 3000.0), new Rango(15.0, 30.0));

        when(servicio.listar()).thenReturn(List.of(helecho, lavanda));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("helecho"))
                .andExpect(jsonPath("$[0].rangos.humedad.min").value(60.0))
                .andExpect(jsonPath("$[0].rangos.humedad.max").value(85.0))
                .andExpect(jsonPath("$[0].rangos.humedad.unidad").value("%"))
                .andExpect(jsonPath("$[0].rangos.luz.min").value(150.0))
                .andExpect(jsonPath("$[0].rangos.luz.max").value(800.0))
                .andExpect(jsonPath("$[0].rangos.luz.unidad").value("lux"))
                .andExpect(jsonPath("$[0].rangos.temperatura.min").value(16.0))
                .andExpect(jsonPath("$[0].rangos.temperatura.max").value(26.0))
                .andExpect(jsonPath("$[0].rangos.temperatura.unidad").value("°C"))
                .andExpect(jsonPath("$[1].nombre").value("lavanda"));
    }

    @Test
    void devuelve200ConListaVaciaCuandoNoHayEspecies() throws Exception {
        when(servicio.listar()).thenReturn(List.of());

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
