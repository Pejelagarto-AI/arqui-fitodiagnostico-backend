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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Descubrimiento "a lo Chromecast" con el contexto real: la matera se registra (o pide reto) desde una IP, el
 * celular pregunta desde otra y solo la ve si comparten red. Las IP se simulan con {@code setRemoteAddr}.
 */
@SpringBootTest(properties = "fitodiagnostico.flota.clave=" + RegistroDeDispositivoIntegracionTest.CLAVE_DE_FLOTA)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DescubrimientoDeDispositivosIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String PREFIJO = "desc-";
    private static final String IP_MATERA = "172.20.10.2";
    private static final String IP_CELULAR = "172.20.10.3";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String serial;
    private String secreto;
    private String usuario;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        // como un serial real (fito- y 6 caracteres); el ZZ inicial lo hace reconocible para limpiar
        serial = "fito-ZZ" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        secreto = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)).substring(0, 43);
        usuario = registrarEIniciarSesion();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from dispositivo where serial like 'fito-ZZ%'");
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'desc-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'desc-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'desc-%')");
        jdbc.update("delete from usuario where correo like 'desc-%'");
    }

    // ---- utilidades ----

    private static RequestPostProcessor desde(String ip) {
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder p, String cuerpo) {
        return p.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder p, String token) {
        return p.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String leer(ResultActions r, String ruta) throws Exception {
        return String.valueOf((Object) JsonPath.read(r.andReturn().getResponse().getContentAsString(), ruta));
    }

    private static String hmac(String clave, String mensaje) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
    }

    private String registrarEIniciarSesion() throws Exception {
        String correo = PREFIJO + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Desc\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private void materaSeRegistraDesde(String ip) throws Exception {
        String firma = hmac(RegistroDeDispositivoIntegracionTest.CLAVE_DE_FLOTA, serial + "\n" + secreto);
        mockMvc.perform(json(post("/api/v1/dispositivos/registro").with(desde(ip)),
                "{\"serial\":\"" + serial + "\",\"secreto\":\"" + secreto + "\",\"firma\":\"" + firma + "\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private ResultActions cercanosDesde(String ip) throws Exception {
        return mockMvc.perform(con(get("/api/v1/dispositivos/cercanos").with(desde(ip)), usuario));
    }

    private String plantaNueva() throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas"), usuario),
                "{\"apodo\":\"Pepa\",\"especie\":\"helecho\"}")).andExpect(status().isCreated()), "$.id");
    }

    private String enmascarado() {
        return serial.substring(0, 7) + "····";
    }

    // ---- aparece ----

    @Test
    void laMateraRecienRegistradaApareceEnmascaradaParaUnCelularDeSuMismaRed() throws Exception {
        materaSeRegistraDesde(IP_MATERA);

        ResultActions r = cercanosDesde(IP_CELULAR).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serialEnmascarado").value(enmascarado()))
                .andExpect(jsonPath("$[0].vistoHaceSegundos").isNumber());
        String cuerpo = r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).doesNotContain(serial).doesNotContain(secreto).doesNotContain(IP_MATERA);
    }

    @Test
    void elEnmascaradoDejaElPrefijoYDosCaracteres() throws Exception {
        String propio = "fito-YUBUD6";
        jdbc.update("delete from dispositivo where serial = ?", propio);
        // una fila propia solo para comprobar el formato exacto del ejemplo del spec
        jdbc.update("insert into dispositivo (serial, secreto, creado_en, registrado_por, ultimo_contacto_en, ultima_ip) "
                + "values (?, ?, ?, 'FLOTA', ?, ?)", propio, secreto, java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now()), IP_MATERA);
        try {
            String cuerpo = cercanosDesde(IP_CELULAR).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(cuerpo).contains("fito-YU····").doesNotContain("YUBUD6");
        } finally {
            jdbc.update("delete from dispositivo where serial = ?", propio);
        }
    }

    @Test
    void elRetoTambienLaHaceAparecerYActualizaLaIp() throws Exception {
        materaSeRegistraDesde("10.0.0.5");
        cercanosDesde(IP_CELULAR).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        // la matera cambia de red (otro Wi-Fi) y pide un reto desde la red del celular
        mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto").with(desde(IP_MATERA))).andExpect(status().isOk());

        cercanosDesde(IP_CELULAR).andExpect(jsonPath("$[0].serialEnmascarado").value(enmascarado()));
        assertThat(jdbc.queryForObject("select ultima_ip from dispositivo where serial = ?", String.class, serial))
                .isEqualTo(IP_MATERA);
    }

    @Test
    void laCredencialRechazadaIgualAnotaElContacto() throws Exception {
        materaSeRegistraDesde("10.0.0.5");
        jdbc.update("update dispositivo set ultimo_contacto_en = ? where serial = ?",
                java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)), serial);

        // sin reclamar: 409, pero la matera habló y eso queda anotado
        mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto").with(desde(IP_MATERA))).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/dispositivos/" + serial + "/credencial").with(desde(IP_MATERA)),
                "{\"respuesta\":\"x\"}")).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select ultima_ip from dispositivo where serial = ?", String.class, serial))
                .isEqualTo(IP_MATERA);
        cercanosDesde(IP_CELULAR).andExpect(jsonPath("$[0].serialEnmascarado").value(enmascarado()));
    }

    // ---- no aparece ----

    @Test
    void desdeOtraRedNoAparece() throws Exception {
        materaSeRegistraDesde(IP_MATERA);

        cercanosDesde("172.20.11.3").andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        cercanosDesde("192.168.1.9").andExpect(jsonPath("$").isEmpty());
        cercanosDesde("8.8.8.8").andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void conIpPublicaIgualAparece() throws Exception {
        materaSeRegistraDesde("190.25.4.4");

        cercanosDesde("190.25.4.4").andExpect(jsonPath("$[0].serialEnmascarado").value(enmascarado()));
        cercanosDesde("190.25.4.5").andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aLos30SegundosSinContactoDesaparece() throws Exception {
        materaSeRegistraDesde(IP_MATERA);
        cercanosDesde(IP_CELULAR).andExpect(jsonPath("$[0]").exists());

        jdbc.update("update dispositivo set ultimo_contacto_en = ? where serial = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(45)), serial);

        cercanosDesde(IP_CELULAR).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void alSerReclamadaDesaparece() throws Exception {
        materaSeRegistraDesde(IP_MATERA);
        cercanosDesde(IP_CELULAR).andExpect(jsonPath("$[0]").exists());

        mockMvc.perform(json(con(post("/api/v1/plantas/" + plantaNueva() + "/dispositivos"), usuario),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isCreated());

        cercanosDesde(IP_CELULAR).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unaMateraDeFabricaQueNuncaHabloNoApareceAunqueEsteSinReclamar() throws Exception {
        jdbc.update("insert into dispositivo (serial, secreto, creado_en, registrado_por) values (?, ?, ?, 'ADMIN')",
                serial, secreto, java.sql.Timestamp.from(Instant.now()));

        cercanosDesde(IP_CELULAR).andExpect(jsonPath("$").isEmpty());
    }

    // ---- seguridad ----

    @Test
    void sinSesionEs401() throws Exception {
        materaSeRegistraDesde(IP_MATERA);

        mockMvc.perform(get("/api/v1/dispositivos/cercanos").with(desde(IP_CELULAR)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeDispositivoEs403() throws Exception {
        materaSeRegistraDesde(IP_MATERA);
        mockMvc.perform(json(con(post("/api/v1/plantas/" + plantaNueva() + "/dispositivos"), usuario),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isCreated());
        String reto = leer(mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto").with(desde(IP_MATERA)))
                .andExpect(status().isOk()), "$.reto");
        String respuesta = hmac(secreto, reto);
        String token = leer(mockMvc.perform(json(post("/api/v1/dispositivos/" + serial + "/credencial").with(desde(IP_MATERA)),
                "{\"respuesta\":\"" + respuesta + "\"}")).andExpect(status().isOk()), "$.token");

        mockMvc.perform(con(get("/api/v1/dispositivos/cercanos").with(desde(IP_MATERA)), token))
                .andExpect(status().isForbidden());
    }

    @Test
    void laRutaNoChocaConLasDelSerial() throws Exception {
        // POST /dispositivos/cercanos no existe (el serial "cercanos" no es una ruta de la matera)
        mockMvc.perform(con(post("/api/v1/dispositivos/cercanos"), usuario)).andExpect(status().is4xxClientError());
        cercanosDesde(IP_CELULAR).andExpect(status().isOk());
    }
}
