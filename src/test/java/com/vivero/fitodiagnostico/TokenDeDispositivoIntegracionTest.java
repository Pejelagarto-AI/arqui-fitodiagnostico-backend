package com.vivero.fitodiagnostico;

import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 2: emisión del token de dispositivo y la política de alcances de la cadena de seguridad, con el
 * contexto real. Dos preguntas: (1) solo el dueño puede emitir; (2) un token de dispositivo no abre nada
 * que no sea su endpoint, y un token de persona no abre el del dispositivo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenDeDispositivoIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    private static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    private static final String RUTA_LECTURAS = "/api/v1/dispositivos/esp32-01/lecturas";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtDecoder decodificador;
    @Autowired private RepositorioAccesos accesos;

    private String dueno;
    private String invitado;
    private String ajeno;
    private String idInvitado;
    private String planta;
    private String tokenDeDispositivo;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        dueno = registrarEIniciarSesion();
        invitado = registrarEIniciarSesion();
        ajeno = registrarEIniciarSesion();
        idInvitado = leer(mockMvc.perform(con(get("/api/v1/cuenta"), invitado)), "$.id");
        planta = leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"Pepa\",\"especie\":\"potos\"}")).andExpect(status().isCreated()), "$.id");
        accesos.otorgar(AccesoCompartido.otorgadoAhora(UUID.fromString(planta), UUID.fromString(idInvitado)));
        tokenDeDispositivo = leer(vincular(dueno, planta, "esp32-01").andExpect(status().isCreated()), "$.token");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from acceso_compartido where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'disp-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'disp-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'disp-%')");
        jdbc.update("delete from usuario where correo like 'disp-%'");
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
        String correo = "disp-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Disp\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private ResultActions vincular(String token, String plantaId, String dispositivoId) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/plantas/" + plantaId + "/dispositivos"), token),
                "{\"dispositivoId\":\"" + dispositivoId + "\"}"));
    }

    private void afirmarDenegadoConCuerpoUniforme(ResultActions r) throws Exception {
        r.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    // ---- emisión: solo el dueño ----

    @Test
    void elDuenoRecibeEl201ConElTokenYSuCaducidad() throws Exception {
        vincular(dueno, planta, "esp32-02")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dispositivoId").value("esp32-02"))
                .andExpect(jsonPath("$.plantaId").value(planta))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expira").isNotEmpty());
    }

    @Test
    void elTokenEmitidoEsUnJwtDeDispositivoLimitadoAEsaPlanta() {
        Jwt jwt = decodificador.decode(tokenDeDispositivo);

        assertThat(jwt.getSubject()).isEqualTo("esp32-01");
        assertThat(jwt.getClaimAsString("tipo")).isEqualTo("dispositivo");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("lecturas:reportar");
        assertThat(jwt.getClaimAsString("planta")).isEqualTo(planta);
    }

    @Test
    void unInvitadoRecibe403UnAjenoRecibe404YSinTokenRecibe401() throws Exception {
        vincular(invitado, planta, "esp32-03").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        vincular(ajeno, planta, "esp32-03").andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/v1/plantas/" + planta + "/dispositivos"), "{\"dispositivoId\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unIdentificadorInvalidoEs400() throws Exception {
        vincular(dueno, planta, "con espacios").andExpect(status().isBadRequest());
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/dispositivos"), dueno), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unTokenDeDispositivoNoPuedeEmitirOtroToken() throws Exception {
        afirmarDenegadoConCuerpoUniforme(vincular(tokenDeDispositivo, planta, "otro"));
    }

    // ---- alcance: el dispositivo solo abre su endpoint ----

    @Test
    void unTokenDeDispositivoSeRechazaConUn403UniformeEnLosEndpointsDePersonas() throws Exception {
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(get("/api/v1/plantas"), tokenDeDispositivo)));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(get("/api/v1/plantas/" + planta), tokenDeDispositivo)));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnostico"), tokenDeDispositivo)));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/mediciones"), tokenDeDispositivo),
                "{\"humedad\":55,\"luz\":700,\"temperatura\":22}")));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(get("/api/v1/cuenta"), tokenDeDispositivo)));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(put("/api/v1/plantas/" + planta), tokenDeDispositivo)));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(delete("/api/v1/plantas/" + planta), tokenDeDispositivo)));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(get("/api/v1/admin/usuarios"), tokenDeDispositivo)));
    }

    @Test
    void unTokenDeDispositivoTampocoAbreLosEndpointsPublicos() throws Exception {
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(get("/api/v1/especies"), tokenDeDispositivo)));
        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(json(con(post("/api/v1/diagnosticos"), tokenDeDispositivo),
                "{\"especie\":\"potos\",\"humedad\":55,\"luz\":700,\"temperatura\":22}")));
    }

    @Test
    void losEndpointsPublicosSiguenAbiertosSinTokenYConTokenDePersona() throws Exception {
        mockMvc.perform(get("/api/v1/especies")).andExpect(status().isOk());
        mockMvc.perform(con(get("/api/v1/especies"), dueno)).andExpect(status().isOk());
    }

    @Test
    void elEndpointDelDispositivoDejaPasarSuTokenYRechazaAlDePersonaConUn403() throws Exception {
        // La cadena abre la puerta al dispositivo: lo que responda el adaptador (404 sin adaptador, 400, 201)
        // no es 401 ni 403.
        int conTokenDeDispositivo = mockMvc.perform(con(post(RUTA_LECTURAS), tokenDeDispositivo))
                .andReturn().getResponse().getStatus();
        assertThat(conTokenDeDispositivo).isNotIn(401, 403);

        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(post(RUTA_LECTURAS), dueno)));
    }

    @Test
    void elEndpointDelDispositivoSinTokenEs401() throws Exception {
        mockMvc.perform(post(RUTA_LECTURAS)).andExpect(status().isUnauthorized());
    }

    @Test
    void unAdministradorConTokenDePersonaNoAbreElEndpointDelDispositivo() throws Exception {
        String admin = leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + ADMIN_CORREO + "\",\"contrasena\":\"" + ADMIN_CONTRASENA + "\"}")), "$.token");

        afirmarDenegadoConCuerpoUniforme(mockMvc.perform(con(post(RUTA_LECTURAS), admin)));
    }

    @Test
    void unTokenDeDispositivoManipuladoEs401() throws Exception {
        String[] partes = tokenDeDispositivo.split("\\.");
        String alterado = partes[0] + "." + partes[1] + "x." + partes[2];

        mockMvc.perform(con(post(RUTA_LECTURAS), alterado)).andExpect(status().isUnauthorized());
    }
}
