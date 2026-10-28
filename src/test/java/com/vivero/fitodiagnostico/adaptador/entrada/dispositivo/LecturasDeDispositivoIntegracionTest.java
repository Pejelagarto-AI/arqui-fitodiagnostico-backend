package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El adaptador de entrada del dispositivo con el contexto real: seguridad, aplicacion, JPA y Flyway.
 * Lo que se prueba es la traduccion del borde (token y ruta -> planta, cuerpo -> objetos de dominio,
 * errores -> cuerpo uniforme) y que el resultado quede en el historial con origen DISPOSITIVO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LecturasDeDispositivoIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String RUTA = "/api/v1/dispositivos/esp32-01/lecturas";
    private static final String MARCA = "2026-10-19T14:30:05Z";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String dueno;
    private String planta;
    private String otraPlanta;
    private String tokenDelDispositivo;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        dueno = registrarEIniciarSesion();
        planta = crearPlanta("Pepa", "potos");           // potos: humedad 40-70, luz 300-1200, temperatura 18-30
        otraPlanta = crearPlanta("Otra", "helecho");
        tokenDelDispositivo = emitirToken(planta, "esp32-01");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'lect-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'lect-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'lect-%')");
        jdbc.update("delete from usuario where correo like 'lect-%'");
    }

    // ---- utilidades ----

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder p, String cuerpo) {
        return p.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder p, String token) {
        return p.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String leer(ResultActions r, String ruta) throws Exception {
        return String.valueOf((Object) JsonPath.read(r.andReturn().getResponse().getContentAsString(), ruta));
    }

    private String registrarEIniciarSesion() throws Exception {
        String correo = "lect-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Lect\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private String crearPlanta(String apodo, String especie) throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"" + apodo + "\",\"especie\":\"" + especie + "\"}")).andExpect(status().isCreated()), "$.id");
    }

    private String emitirToken(String plantaId, String dispositivoId) throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas/" + plantaId + "/dispositivos"), dueno),
                "{\"dispositivoId\":\"" + dispositivoId + "\"}")).andExpect(status().isCreated()), "$.token");
    }

    private ResultActions reportar(String token, String cuerpo) throws Exception {
        return mockMvc.perform(json(con(post(RUTA), token), cuerpo));
    }

    private static String lectura(String humedad, String luz, String temperatura) {
        return "{\"marcaDeTiempo\":\"" + MARCA + "\",\"humedad\":" + humedad + ",\"luz\":" + luz
                + ",\"temperatura\":" + temperatura + "}";
    }

    private int diagnosticosDe(String plantaId) {
        return jdbc.queryForObject("select count(*) from diagnostico_planta where planta_id = ?", Integer.class,
                UUID.fromString(plantaId));
    }

    // ---- lecturas validas ----

    @Test
    void unaLecturaDentroDelOptimoResponde201ConElDiagnosticoYQuedaEnElHistorialConOrigenDispositivo() throws Exception {
        reportar(tokenDelDispositivo, lectura("55", "700", "22"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("DISPOSITIVO"))
                .andExpect(jsonPath("$.plantaId").value(planta))
                .andExpect(jsonPath("$.tomadaEn").value(MARCA))
                .andExpect(jsonPath("$.diagnostico.especie").value("potos"))
                .andExpect(jsonPath("$.diagnostico.estado").value("SALUDABLE"))
                .andExpect(jsonPath("$.diagnostico.parametros.length()").value(3));

        // El dueno la ve como el diagnostico vigente de su planta, con la hora que informo el dispositivo.
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnostico"), dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayDiagnostico").value(true))
                .andExpect(jsonPath("$.diagnostico.origen").value("DISPOSITIVO"))
                .andExpect(jsonPath("$.diagnostico.tomadaEn").value(MARCA));
    }

    @Test
    void unaLecturaFueraDeRangoEsValidaYElDiagnosticoLoDice() throws Exception {
        reportar(tokenDelDispositivo, lectura("5", "700", "22"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diagnostico.estado").value("CRITICO"));
    }

    @Test
    void laPlantaSaleDelTokenNoDeLaRutaNiDelCuerpo() throws Exception {
        reportar(tokenDelDispositivo, lectura("55", "700", "22")).andExpect(status().isCreated());

        assertThat(diagnosticosDe(planta)).isEqualTo(1);
        assertThat(diagnosticosDe(otraPlanta)).isZero();
    }

    // ---- datos invalidos: 400 con el cuerpo uniforme, y nada se guarda ----

    @Test
    void unCampoAusenteEs400IndicandoElCampo() throws Exception {
        reportar(tokenDelDispositivo, "{\"marcaDeTiempo\":\"" + MARCA + "\",\"humedad\":55,\"temperatura\":22}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("luz"));
        assertThat(diagnosticosDe(planta)).isZero();
    }

    @Test
    void unaMarcaDeTiempoAusenteOMalFormadaEs400() throws Exception {
        reportar(tokenDelDispositivo, "{\"humedad\":55,\"luz\":700,\"temperatura\":22}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("marcaDeTiempo"));
        reportar(tokenDelDispositivo,
                "{\"marcaDeTiempo\":\"ayer por la tarde\",\"humedad\":55,\"luz\":700,\"temperatura\":22}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("marcaDeTiempo"));
        assertThat(diagnosticosDe(planta)).isZero();
    }

    @Test
    void unValorNoNumericoEs400() throws Exception {
        reportar(tokenDelDispositivo, lectura("\"abc\"", "700", "22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("humedad"));
        assertThat(diagnosticosDe(planta)).isZero();
    }

    @Test
    void unValorFisicamenteImposibleEs400() throws Exception {
        reportar(tokenDelDispositivo, lectura("150", "700", "22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("humedad"));
        reportar(tokenDelDispositivo, lectura("55", "-1", "22")).andExpect(status().isBadRequest());
        reportar(tokenDelDispositivo, lectura("55", "700", "200")).andExpect(status().isBadRequest());
        assertThat(diagnosticosDe(planta)).isZero();
    }

    @Test
    void unJsonRotoEs400() throws Exception {
        reportar(tokenDelDispositivo, "{\"humedad\": 55, \"luz\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
    }

    // ---- identidad: 401 y 403 ----

    @Test
    void sinTokenEs401() throws Exception {
        mockMvc.perform(json(post(RUTA), lectura("55", "700", "22"))).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDePersonaEs403() throws Exception {
        reportar(dueno, lectura("55", "700", "22"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"));
        assertThat(diagnosticosDe(planta)).isZero();
    }

    @Test
    void unDispositivoNoPuedeReportarPorOtroDispositivo() throws Exception {
        // El token es de esp32-01; la ruta dice esp32-02.
        mockMvc.perform(json(con(post("/api/v1/dispositivos/esp32-02/lecturas"), tokenDelDispositivo),
                lectura("55", "700", "22")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        assertThat(diagnosticosDe(planta)).isZero();
    }

    @Test
    void siSeEliminaLaPlantaElTokenYaNoSirveParaReportar() throws Exception {
        mockMvc.perform(con(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/plantas/" + planta), dueno)).andExpect(status().isNoContent());

        reportar(tokenDelDispositivo, lectura("55", "700", "22")).andExpect(status().isNotFound());
    }
}
