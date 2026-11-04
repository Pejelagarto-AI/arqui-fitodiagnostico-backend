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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 3b con el contexto real y la clave de flota configurada: el chip se registra solo (201), repetirlo es
 * idempotente (200), pisar a una matera con otro secreto es 409, una firma mala es 401, y el flujo completo
 * (registro, reto + credencial 409 sin reclamar, reclamo, credencial 200) funciona sin que nadie toque una
 * credencial. El caso "sin clave configurada" vive en {@link RegistroSinFlotaIntegracionTest}.
 */
@SpringBootTest(properties = "fitodiagnostico.flota.clave=" + RegistroDeDispositivoIntegracionTest.CLAVE_DE_FLOTA)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroDeDispositivoIntegracionTest {

    static final String CLAVE_DE_FLOTA = "clave-de-flota-solo-para-pruebas-0123456789";
    private static final String CLAVE = "clave-larga-1";
    private static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    private static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    private static final String PREFIJO = "flot-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String serial;
    private String secreto;

    @BeforeEach
    void preparar() {
        limpiar();
        serial = PREFIJO + UUID.randomUUID().toString().substring(0, 6);
        secreto = Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                .substring(0, 43);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from dispositivo where serial like 'flot-%'");
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'flot-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'flot-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'flot-%')");
        jdbc.update("delete from usuario where correo like 'flot-%'");
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

    private static String hmac(String clave, String mensaje) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
    }

    /** Lo que calcula el firmware: base64( HMAC-SHA256( clave de flota, serial + "\n" + secreto ) ). */
    private String firma(String s, String sec) throws Exception {
        return hmac(CLAVE_DE_FLOTA, s + "\n" + sec);
    }

    private ResultActions registrar(String s, String sec, String firma) throws Exception {
        return mockMvc.perform(json(post("/api/v1/dispositivos/registro"),
                "{\"serial\":\"" + s + "\",\"secreto\":\"" + sec + "\",\"firma\":\"" + firma + "\"}"));
    }

    private ResultActions registrarBien() throws Exception {
        return registrar(serial, secreto, firma(serial, secreto));
    }

    private String iniciarSesion(String correo, String contrasena) throws Exception {
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private String duenoConPlanta(String[] planta) throws Exception {
        String correo = PREFIJO + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Flota\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        String token = iniciarSesion(correo, CLAVE);
        planta[0] = leer(mockMvc.perform(json(con(post("/api/v1/plantas"), token),
                "{\"apodo\":\"Pepa\",\"especie\":\"potos\"}")).andExpect(status().isCreated()), "$.id");
        return token;
    }

    // ---- el contrato del registro ----

    @Test
    void unRegistroConFirmaValidaEs201SinReclamarYSinFiltrarElSecreto() throws Exception {
        registrarBien().andExpect(status().isCreated())
                .andExpect(jsonPath("$.serial").value(serial))
                .andExpect(jsonPath("$.estado").value("SIN_RECLAMAR"))
                .andExpect(jsonPath("$.creadoEn").isNotEmpty())
                .andExpect(jsonPath("$.secreto").doesNotExist());
        assertThat(jdbc.queryForObject("select registrado_por from dispositivo where serial = ?", String.class, serial))
                .isEqualTo("FLOTA");
    }

    @Test
    void registrarDeNuevoConElMismoSecretoEs200ConElMismoCuerpo() throws Exception {
        String creadoEn = leer(registrarBien().andExpect(status().isCreated()), "$.creadoEn");

        registrarBien().andExpect(status().isOk()).andExpect(jsonPath("$.serial").value(serial))
                .andExpect(jsonPath("$.estado").value("SIN_RECLAMAR"))
                .andExpect(jsonPath("$.creadoEn").value(creadoEn));
        assertThat(jdbc.queryForObject("select count(*) from dispositivo where serial = ?", Integer.class, serial)).isEqualTo(1);
    }

    @Test
    void elMismoSerialConOtroSecretoEs409AunqueLaFirmaSeaValidaYNoPisaAlOriginal() throws Exception {
        registrarBien().andExpect(status().isCreated());
        String otro = "x" + secreto.substring(1);

        registrar(serial, otro, firma(serial, otro)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_YA_EXISTE"));
        assertThat(jdbc.queryForObject("select secreto from dispositivo where serial = ?", String.class, serial))
                .isEqualTo(secreto);
    }

    @Test
    void unaFirmaMalaEs401YNoCreaNada() throws Exception {
        registrar(serial, secreto, "firma-que-no-es-el-hmac").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("FIRMA_DE_FLOTA_INVALIDA"));
        registrar(serial, secreto, hmac("otra-clave-de-flota-que-no-es-la-buena-000", serial + "\n" + secreto))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from dispositivo where serial = ?", Integer.class, serial)).isZero();
    }

    @Test
    void unaFirmaMalaEs401IgualParaUnSerialQueYaExisteYParaUnoQueNo() throws Exception {
        registrarBien().andExpect(status().isCreated());

        registrar(serial, secreto, "mala").andExpect(status().isUnauthorized());
        registrar(serial + "x", secreto, "mala").andExpect(status().isUnauthorized());
    }

    @Test
    void firmarSinElSaltoDeLineaNoValeAunqueSeaElMismoHmacDeOtroMensaje() throws Exception {
        registrar(serial, secreto, hmac(CLAVE_DE_FLOTA, serial + secreto)).andExpect(status().isUnauthorized());
    }

    @Test
    void unSerialMalformadoUnSecretoCortoOUnCuerpoIncompletoSon400() throws Exception {
        registrar("con espacios", secreto, firma("con espacios", secreto)).andExpect(status().isBadRequest());
        registrar(serial, "corto", firma(serial, "corto")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        mockMvc.perform(json(post("/api/v1/dispositivos/registro"), "{}")).andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/v1/dispositivos/registro"), "{\"serial\":\"" + serial + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laRutaNoPideAuthorization() throws Exception {
        registrarBien().andExpect(status().isCreated());   // sin cabecera alguna
    }

    // ---- el flujo completo, sin que nadie toque una credencial ----

    @Test
    void registroRetoYCredencialDan409SinReclamarYDespuesDelReclamoDan200() throws Exception {
        String[] planta = new String[1];
        String dueno = duenoConPlanta(planta);
        registrarBien().andExpect(status().isCreated());

        String reto = leer(mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto")).andExpect(status().isOk()), "$.reto");
        mockMvc.perform(json(post("/api/v1/dispositivos/" + serial + "/credencial"),
                "{\"respuesta\":\"" + hmac(secreto, reto) + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_SIN_RECLAMAR"));

        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta[0] + "/dispositivos"), dueno),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isCreated());

        String reto2 = leer(mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto")).andExpect(status().isOk()), "$.reto");
        ResultActions credencial = mockMvc.perform(json(post("/api/v1/dispositivos/" + serial + "/credencial"),
                "{\"respuesta\":\"" + hmac(secreto, reto2) + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.plantaId").value(planta[0]));
        String token = leer(credencial, "$.token");

        mockMvc.perform(json(con(post("/api/v1/dispositivos/" + serial + "/lecturas"), token),
                "{\"marcaDeTiempo\":\"2026-11-03T14:30:05Z\",\"humedad\":55,\"luz\":700,\"temperatura\":22}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.origen").value("DISPOSITIVO"));

        // reiniciarse y volver a registrarse no desvincula nada
        registrarBien().andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECLAMADO"));
    }

    // ---- reclamo antes de que la matera prenda ----

    @Test
    void reclamarUnSerialQueTodaviaNoSeRegistroEs404ConUnMensajeQueDiceQueHacer() throws Exception {
        String[] planta = new String[1];
        String dueno = duenoConPlanta(planta);

        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta[0] + "/dispositivos"), dueno),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("no existe o la matera no ha encendido todavía"));

        registrarBien().andExpect(status().isCreated());   // la matera prende
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta[0] + "/dispositivos"), dueno),
                "{\"serial\":\"" + serial + "\"}")).andExpect(status().isCreated());
    }

    // ---- auditoria ----

    @Test
    void elListadoDelAdminMuestraQuienRegistroCadaDispositivo() throws Exception {
        String admin = iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA);
        registrarBien().andExpect(status().isCreated());
        String delAdmin = PREFIJO + "adm" + UUID.randomUUID().toString().substring(0, 4);
        mockMvc.perform(json(con(post("/api/v1/admin/dispositivos"), admin), "{\"serial\":\"" + delAdmin + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(con(get("/api/v1/admin/dispositivos"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serial == '" + serial + "')].registradoPor").value("FLOTA"))
                .andExpect(jsonPath("$[?(@.serial == '" + delAdmin + "')].registradoPor").value("ADMIN"))
                .andExpect(jsonPath("$[?(@.serial == '" + serial + "')].secreto").isEmpty());
    }
}
