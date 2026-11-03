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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 3, lado del chip, con el contexto real: reto, respuesta HMAC calculada aparte (como la calcularía el
 * ESP32 o el simulador) y credencial. Lo que se prueba es el contrato de punta a punta: el feliz, cada
 * rechazo (409 sin reclamar, 401 por respuesta mala, reto vencido o reusado), que desvincular corta la
 * renovación y que el token obtenido sirve para reportar lecturas y para nada más.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutenticacionDeDispositivoIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    private static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    private static final String PREFIJO = "auth-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtDecoder decodificador;

    private String dueno;
    private String planta;
    private String serial;
    private String secreto;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        String admin = iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA);
        dueno = registrarEIniciarSesion();
        planta = leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"Pepa\",\"especie\":\"potos\"}")).andExpect(status().isCreated()), "$.id");
        serial = PREFIJO + UUID.randomUUID().toString().substring(0, 6);
        ResultActions fabrica = mockMvc.perform(json(con(post("/api/v1/admin/dispositivos"), admin),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isCreated());
        secreto = leer(fabrica, "$.secreto");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from dispositivo where serial like 'auth-%'");
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'auth-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'auth-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'auth-%')");
        jdbc.update("delete from usuario where correo like 'auth-%'");
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

    private String iniciarSesion(String correo, String contrasena) throws Exception {
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private String registrarEIniciarSesion() throws Exception {
        String correo = PREFIJO + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Auth\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return iniciarSesion(correo, CLAVE);
    }

    private void reclamar() throws Exception {
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/dispositivos"), dueno),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isCreated());
    }

    /** Lo que calcula el chip: base64( HMAC-SHA256( clave = secreto como texto, mensaje = reto como texto ) ). */
    private String responder(String reto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(reto.getBytes(StandardCharsets.UTF_8)));
    }

    private String pedirReto() throws Exception {
        return leer(mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto")).andExpect(status().isOk()), "$.reto");
    }

    private ResultActions pedirCredencial(String respuesta) throws Exception {
        return mockMvc.perform(json(post("/api/v1/dispositivos/" + serial + "/credencial"),
                "{\"respuesta\":\"" + respuesta + "\"}"));
    }

    private String tokenDelChip() throws Exception {
        reclamar();
        return leer(pedirCredencial(responder(pedirReto())).andExpect(status().isOk()), "$.token");
    }

    private void afirmarRechazada(ResultActions r) throws Exception {
        r.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("CREDENCIAL_RECHAZADA"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    // ---- el camino feliz ----

    @Test
    void retoYCredencialSinTokenDanAlChipUnTokenCortoDeSuPlanta() throws Exception {
        reclamar();

        ResultActions reto = mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reto").isNotEmpty())
                .andExpect(jsonPath("$.expira").isNotEmpty());
        String valor = leer(reto, "$.reto");
        assertThat(Base64.getDecoder().decode(valor)).hasSize(32);                      // base64 estandar de 32 bytes
        Instant expiraReto = Instant.parse(leer(reto, "$.expira"));
        assertThat(Duration.between(Instant.now(), expiraReto)).isBetween(Duration.ofSeconds(55), Duration.ofSeconds(61));

        ResultActions credencial = pedirCredencial(responder(valor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.plantaId").value(planta))
                .andExpect(jsonPath("$.expira").isNotEmpty());
        Jwt jwt = decodificador.decode(leer(credencial, "$.token"));
        assertThat(jwt.getSubject()).isEqualTo(serial);
        assertThat(jwt.getClaimAsString("tipo")).isEqualTo("dispositivo");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("lecturas:reportar");
        assertThat(jwt.getClaimAsString("planta")).isEqualTo(planta);
        // por defecto el token dura una hora (PT1H): el chip renueva solo
        assertThat(Duration.between(Instant.now(), jwt.getExpiresAt()))
                .isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
    }

    @Test
    void elDuenoVeConectadoAlDispositivoTrasLaCredencial() throws Exception {
        reclamar();
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), dueno))
                .andExpect(jsonPath("$[0].ultimaCredencialEn").value((Object) null));

        pedirCredencial(responder(pedirReto())).andExpect(status().isOk());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), dueno))
                .andExpect(jsonPath("$[0].serial").value(serial))
                .andExpect(jsonPath("$[0].ultimaCredencialEn").isNotEmpty());
    }

    @Test
    void lasRutasDelChipNoPidenAuthorization() throws Exception {
        reclamar();
        // sin ninguna cabecera: ni 401 ni 403 de la cadena de seguridad
        mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto")).andExpect(status().isOk());
    }

    // ---- rechazos ----

    @Test
    void sinReclamarLaCredencialEs409YElRetoSeConsumeIgual() throws Exception {
        String reto = pedirReto();

        pedirCredencial(responder(reto)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_SIN_RECLAMAR"));

        // el dueno lo reclama, pero el reto de antes ya no sirve: el chip debe pedir otro
        reclamar();
        afirmarRechazada(pedirCredencial(responder(reto)));
        pedirCredencial(responder(pedirReto())).andExpect(status().isOk());
    }

    @Test
    void unaRespuestaMalaEs401YElRetoQuedaGastado() throws Exception {
        reclamar();
        String reto = pedirReto();

        afirmarRechazada(pedirCredencial("respuesta-que-no-es-el-hmac"));
        afirmarRechazada(pedirCredencial(responder(reto)));   // ni siquiera la buena sirve ya sobre ese reto
    }

    @Test
    void unaRespuestaCalculadaConOtroSecretoEs401() throws Exception {
        reclamar();
        String reto = pedirReto();
        String esperado = responder(reto);
        secreto = "otro-" + secreto;

        afirmarRechazada(pedirCredencial(responder(reto)));
        assertThat(esperado).isNotEqualTo(responder(reto));
    }

    @Test
    void sinHaberPedidoRetoEs401() throws Exception {
        reclamar();

        afirmarRechazada(pedirCredencial(responder("un-reto-inventado")));
    }

    @Test
    void unRetoExpiradoEs401AunqueLaRespuestaSeaCorrecta() throws Exception {
        reclamar();
        String reto = pedirReto();
        jdbc.update("update dispositivo set reto_expira = ? where serial = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), serial);

        afirmarRechazada(pedirCredencial(responder(reto)));
    }

    @Test
    void unRetoReusadoEs401() throws Exception {
        reclamar();
        String reto = pedirReto();
        pedirCredencial(responder(reto)).andExpect(status().isOk());

        afirmarRechazada(pedirCredencial(responder(reto)));
    }

    @Test
    void pedirOtroRetoReemplazaAlAnterior() throws Exception {
        reclamar();
        String primero = pedirReto();
        String segundo = pedirReto();
        assertThat(segundo).isNotEqualTo(primero);

        afirmarRechazada(pedirCredencial(responder(primero)));
    }

    @Test
    void unCuerpoSinRespuestaEs400() throws Exception {
        mockMvc.perform(json(post("/api/v1/dispositivos/" + serial + "/credencial"), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unSerialQueNoExisteEs404EnRetoYEnCredencial() throws Exception {
        mockMvc.perform(post("/api/v1/dispositivos/auth-no-existe/reto")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_NO_ENCONTRADO"));
        mockMvc.perform(json(post("/api/v1/dispositivos/auth-no-existe/credencial"), "{\"respuesta\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- desvincular ----

    @Test
    void desvincularImpideCredencialNuevaPeroUnaVueltaAReclamarLaRestituye() throws Exception {
        reclamar();
        pedirCredencial(responder(pedirReto())).andExpect(status().isOk());
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/dispositivos/" + serial), dueno))
                .andExpect(status().isNoContent());

        pedirCredencial(responder(pedirReto())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_SIN_RECLAMAR"));

        reclamar();
        pedirCredencial(responder(pedirReto())).andExpect(status().isOk());
    }

    // ---- lo que el token abre ----

    @Test
    void elTokenObtenidoSirveParaReportarLecturasYNoParaLosEndpointsDeUsuario() throws Exception {
        String token = tokenDelChip();

        mockMvc.perform(json(con(post("/api/v1/dispositivos/" + serial + "/lecturas"), token),
                "{\"marcaDeTiempo\":\"2026-11-03T14:30:05Z\",\"humedad\":55,\"luz\":700,\"temperatura\":22}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.origen").value("DISPOSITIVO"))
                .andExpect(jsonPath("$.plantaId").value(planta));

        for (String ruta : new String[] {"/api/v1/plantas", "/api/v1/plantas/" + planta,
                "/api/v1/plantas/" + planta + "/dispositivos", "/api/v1/cuenta", "/api/v1/admin/dispositivos",
                "/api/v1/admin/usuarios"}) {
            mockMvc.perform(con(get(ruta), token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"));
        }
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/dispositivos"), token),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isForbidden());
        mockMvc.perform(json(con(post("/api/v1/admin/dispositivos"), token), "{}")).andExpect(status().isForbidden());
    }

    @Test
    void elTokenDeUnDispositivoNoReportaPorOtroDispositivo() throws Exception {
        String token = tokenDelChip();

        mockMvc.perform(json(con(post("/api/v1/dispositivos/otro-serial/lecturas"), token),
                "{\"marcaDeTiempo\":\"2026-11-03T14:30:05Z\",\"humedad\":55,\"luz\":700,\"temperatura\":22}"))
                .andExpect(status().isForbidden());
    }
}
