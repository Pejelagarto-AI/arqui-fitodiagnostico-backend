package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorTokenDispositivoPort;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La salud de la matera de punta a punta con el contexto real: seguridad, aplicacion, JPA y Flyway.
 * Lo que se prueba es la traduccion del borde (token y ruta -> planta, cuerpo -> objetos de dominio,
 * errores -> cuerpo uniforme) y que el resultado quede en el historial con origen DISPOSITIVO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaludDeDispositivoIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String RUTA = "/api/v1/dispositivos/esp32-sal/salud";
    private static final String MARCA = "2026-10-19T14:30:05Z";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmisorTokenDispositivoPort emisor;

    private String dueno;
    private String planta;
    private String otraPlanta;
    private String tokenDelDispositivo;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        dueno = registrarEIniciarSesion();
        planta = crearPlanta("Pepa", "helecho");           // helecho: humedad 60-85, luz 150-800, temperatura 16-26
        otraPlanta = crearPlanta("Otra", "helecho");
        tokenDelDispositivo = emitirToken(planta, "esp32-sal");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from dispositivo where serial like 'esp32-%'");
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'sal-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'sal-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'sal-%')");
        jdbc.update("delete from usuario where correo like 'sal-%'");
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
        String correo = "sal-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
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

    /** El token se emite directo por el puerto: aqui se prueba el endpoint de lecturas, no la obtencion de la credencial. */
    private String emitirToken(String plantaId, String serial) {
        // Desde la D96 una lectura exige que el dispositivo siga reclamado por la planta del token: se deja la fila.
        jdbc.update("delete from dispositivo where serial = ?", serial);
        jdbc.update("insert into dispositivo (serial, secreto, planta_id, reclamado_en, creado_en, registrado_por) "
                + "values (?, 's', ?, now(), now(), 'ADMIN')", serial, UUID.fromString(plantaId));
        return emisor.emitir(serial, UUID.fromString(plantaId)).valor();
    }

    // ---- salud ----

    private ResultActions salud(String token, String cuerpo) throws Exception {
        return mockMvc.perform(json(con(post(RUTA), token), cuerpo));
    }

    private static final String TEMPERATURA_FALLA =
            "{\"sensores\":{\"humedad\":\"OK\",\"luz\":\"OK\",\"temperatura\":\"FALLA\"},\"detalle\":\"dht22 sin respuesta\"}";
    private static final String TODO_OK =
            "{\"sensores\":{\"humedad\":\"OK\",\"luz\":\"OK\",\"temperatura\":\"OK\"}}";

    @Test
    void unaFallaSeGuardaYElDuenoLaVeEnLosDispositivosDeLaPlanta() throws Exception {
        salud(tokenDelDispositivo, TEMPERATURA_FALLA).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALLA")).andExpect(jsonPath("$.cambio").value(true));

        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serial").value("esp32-sal"))
                .andExpect(jsonPath("$[0].salud.estado").value("FALLA"))
                .andExpect(jsonPath("$[0].salud.sensores.temperatura").value("FALLA"))
                .andExpect(jsonPath("$[0].salud.sensores.humedad").value("OK"))
                .andExpect(jsonPath("$[0].salud.detalle").value("dht22 sin respuesta"))
                .andExpect(jsonPath("$[0].salud.mensaje").value("no te siento la temperatura"))
                .andExpect(jsonPath("$[0].ultimaLecturaEn").doesNotExist());
    }

    @Test
    void sinNoticiasLaSaludYLaUltimaLecturaVienenNulas() throws Exception {
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].salud").value((Object) null))
                .andExpect(jsonPath("$[0].ultimaLecturaEn").value((Object) null));
    }

    @Test
    void unaLecturaMarcaLaUltimaLecturaYRecuperaDeLaFalla() throws Exception {
        salud(tokenDelDispositivo, TEMPERATURA_FALLA).andExpect(status().isOk());

        mockMvc.perform(json(con(post("/api/v1/dispositivos/esp32-sal/lecturas"), tokenDelDispositivo),
                "{\"marcaDeTiempo\":\"2026-11-16T14:30:05Z\",\"humedad\":70,\"luz\":500,\"temperatura\":22}"))
                .andExpect(status().isCreated());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), dueno))
                .andExpect(jsonPath("$[0].salud.estado").value("OK"))
                .andExpect(jsonPath("$[0].salud.sensores.temperatura").value("OK"))
                .andExpect(jsonPath("$[0].ultimaLecturaEn").isNotEmpty());
    }

    @Test
    void elAdministradorVeLaSaludEnElListadoDeLaFabrica() throws Exception {
        salud(tokenDelDispositivo, TEMPERATURA_FALLA).andExpect(status().isOk());
        jdbc.update("insert into usuario_rol (usuario_id, rol) select u.id, 'ADMINISTRADOR' from usuario u "
                + "where u.correo like 'sal-%'");
        String admin = iniciarSesionDe("sal-");
        mockMvc.perform(con(get("/api/v1/admin/dispositivos"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serial=='esp32-sal')].salud.estado").value("FALLA"));
    }

    @Test
    void sinTokenEs401() throws Exception {
        mockMvc.perform(json(post(RUTA), TODO_OK)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDePersonaEs403() throws Exception {
        salud(dueno, TODO_OK).andExpect(status().isForbidden());
    }

    @Test
    void unTokenDeOtroDispositivoEs403() throws Exception {
        String otro = emitirToken(planta, "esp32-otro");
        salud(otro, TODO_OK).andExpect(status().isForbidden());
    }

    @Test
    void unDispositivoYaNoVinculadoEs401() throws Exception {
        jdbc.update("update dispositivo set planta_id = null, reclamado_en = null where serial = 'esp32-sal'");
        salud(tokenDelDispositivo, TODO_OK).andExpect(status().isUnauthorized());
    }

    @Test
    void unCuerpoInvalidoEs400() throws Exception {
        salud(tokenDelDispositivo, "{\"sensores\":{\"humedad\":\"OK\",\"luz\":\"OK\"}}").andExpect(status().isBadRequest());
        salud(tokenDelDispositivo, "{\"sensores\":{\"humedad\":\"OK\",\"luz\":\"OK\",\"temperatura\":\"ROTO\"}}")
                .andExpect(status().isBadRequest());
        salud(tokenDelDispositivo, "{\"sensores\":{\"humedad\":\"OK\",\"luz\":\"OK\",\"temperatura\":\"OK\",\"gas\":\"OK\"}}")
                .andExpect(status().isBadRequest());
        salud(tokenDelDispositivo, "{}").andExpect(status().isBadRequest());
    }

    private String iniciarSesionDe(String prefijo) throws Exception {
        String correo = jdbc.queryForObject("select correo from usuario where correo like ?", String.class, prefijo + "%");
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }
}
