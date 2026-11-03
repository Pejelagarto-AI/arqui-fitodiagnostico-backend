package com.vivero.fitodiagnostico;

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
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 3, lado humano: la fábrica (administrador) y el reclamo (dueño), con el contexto real. Preguntas:
 * (1) solo un administrador da de alta y ve el secreto una vez; (2) solo el dueño reclama, lista y desvincula
 * (invitado 403, ajeno 404); (3) un serial ya reclamado por otra planta no se roba; (4) borrar la planta
 * suelta sus dispositivos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReclamoDeDispositivoIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    private static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    private static final String PREFIJO = "recl-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RepositorioAccesos accesos;

    private String admin;
    private String dueno;
    private String invitado;
    private String ajeno;
    private String planta;
    private String otraPlanta;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        admin = iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA);
        dueno = registrarEIniciarSesion();
        invitado = registrarEIniciarSesion();
        ajeno = registrarEIniciarSesion();
        String idInvitado = leer(mockMvc.perform(con(get("/api/v1/cuenta"), invitado)), "$.id");
        planta = crearPlanta("Pepa");
        otraPlanta = crearPlanta("Otra");
        accesos.otorgar(AccesoCompartido.otorgadoAhora(UUID.fromString(planta), UUID.fromString(idInvitado)));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from dispositivo where serial like 'recl-%' or planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'recl-%')");
        jdbc.update("delete from dispositivo where serial like 'fito-%' and secreto like 'recl-%'");
        jdbc.update("delete from acceso_compartido where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'recl-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'recl-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'recl-%')");
        jdbc.update("delete from usuario where correo like 'recl-%'");
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
                "{\"nombre\":\"Recl\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return iniciarSesion(correo, CLAVE);
    }

    private String crearPlanta(String apodo) throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"" + apodo + "\",\"especie\":\"potos\"}")).andExpect(status().isCreated()), "$.id");
    }

    private ResultActions fabricar(String token, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder p = con(post("/api/v1/admin/dispositivos"), token);
        return mockMvc.perform(cuerpo == null ? p : json(p, cuerpo));
    }

    /** Da de alta un dispositivo con serial propio (las pruebas lo limpian por prefijo) y lo devuelve. */
    private String fabricarSerial(String sufijo) throws Exception {
        String serial = PREFIJO + sufijo + "-" + UUID.randomUUID().toString().substring(0, 6);
        fabricar(admin, "{\"serial\":\"" + serial + "\"}").andExpect(status().isCreated());
        return serial;
    }

    private ResultActions reclamar(String token, String plantaId, String serial) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/plantas/" + plantaId + "/dispositivos"), token),
                "{\"serial\":\"" + serial + "\"}"));
    }

    // ---- fábrica ----

    @Test
    void elAdministradorFabricaSinCuerpoYRecibeSerialGeneradoYSecretoUnaVez() throws Exception {
        ResultActions r = fabricar(admin, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.serial").value(org.hamcrest.Matchers.matchesPattern("fito-[A-HJ-NP-Z2-9]{6}")))
                .andExpect(jsonPath("$.secreto").isNotEmpty())
                .andExpect(jsonPath("$.creadoEn").isNotEmpty());
        String serial = leer(r, "$.serial");
        // base64url de 32 bytes sin relleno: 43 caracteres
        assertThat(leer(r, "$.secreto")).matches("[A-Za-z0-9_-]{43}");
        jdbc.update("update dispositivo set secreto = 'recl-' || secreto where serial = ?", serial);   // para limpiarlo

        // el listado no vuelve a mostrar el secreto
        mockMvc.perform(con(get("/api/v1/admin/dispositivos"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serial=='" + serial + "')].secreto").isEmpty());
    }

    @Test
    void fabricarConSerialPropioLoRespetaYUnoRepetidoEs409() throws Exception {
        String serial = fabricarSerial("a");

        fabricar(admin, "{\"serial\":\"" + serial + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_YA_EXISTE"));
    }

    @Test
    void fabricarConSerialInvalidoEs400() throws Exception {
        fabricar(admin, "{\"serial\":\"con espacios\"}").andExpect(status().isBadRequest());
    }

    @Test
    void soloUnAdministradorUsaLaFabrica() throws Exception {
        fabricar(dueno, null).andExpect(status().isForbidden());
        mockMvc.perform(con(get("/api/v1/admin/dispositivos"), dueno)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/dispositivos")).andExpect(status().isUnauthorized());
    }

    @Test
    void elAdministradorListaConEstadoYPlantaDeCadaDispositivo() throws Exception {
        String libre = fabricarSerial("libre");
        String suyo = fabricarSerial("suyo");
        reclamar(dueno, planta, suyo).andExpect(status().isCreated());

        mockMvc.perform(con(get("/api/v1/admin/dispositivos"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serial=='" + libre + "')].estado").value("SIN_RECLAMAR"))
                .andExpect(jsonPath("$[?(@.serial=='" + suyo + "')].estado").value("RECLAMADO"))
                .andExpect(jsonPath("$[?(@.serial=='" + suyo + "')].plantaId").value(planta))
                .andExpect(jsonPath("$[?(@.serial=='" + suyo + "')].reclamadoEn").isNotEmpty())
                .andExpect(jsonPath("$[?(@.serial=='" + libre + "')].creadoEn").isNotEmpty());
    }

    // ---- reclamo ----

    @Test
    void elDuenoReclamaYRecibe201SinNingunaCredencial() throws Exception {
        String serial = fabricarSerial("a");

        reclamar(dueno, planta, serial).andExpect(status().isCreated())
                .andExpect(jsonPath("$.serial").value(serial))
                .andExpect(jsonPath("$.plantaId").value(planta))
                .andExpect(jsonPath("$.reclamadoEn").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.secreto").doesNotExist());
    }

    @Test
    void reclamarDeNuevoParaLaMismaPlantaEsIdempotenteYDevuelve201() throws Exception {
        String serial = fabricarSerial("a");
        String primera = leer(reclamar(dueno, planta, serial).andExpect(status().isCreated()), "$.reclamadoEn");

        reclamar(dueno, planta, serial).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reclamadoEn").value(primera));
    }

    @Test
    void unSerialQueNoExisteEs404() throws Exception {
        reclamar(dueno, planta, "recl-no-existe").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_NO_ENCONTRADO"));
    }

    @Test
    void unDispositivoReclamadoPorOtraPlantaEs409() throws Exception {
        String serial = fabricarSerial("a");
        reclamar(dueno, planta, serial).andExpect(status().isCreated());

        reclamar(dueno, otraPlanta, serial).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISPOSITIVO_YA_RECLAMADO"));
    }

    @Test
    void unInvitadoRecibe403UnAjenoRecibe404YSinTokenRecibe401() throws Exception {
        String serial = fabricarSerial("a");

        reclamar(invitado, planta, serial).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        reclamar(ajeno, planta, serial).andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/v1/plantas/" + planta + "/dispositivos"), "{\"serial\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), invitado)).andExpect(status().isForbidden());
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), ajeno)).andExpect(status().isNotFound());
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/dispositivos/" + serial), invitado))
                .andExpect(status().isForbidden());
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/dispositivos/" + serial), ajeno))
                .andExpect(status().isNotFound());
    }

    @Test
    void unCuerpoSinSerialOConSerialInvalidoEs400() throws Exception {
        reclamar(dueno, planta, "con espacios").andExpect(status().isBadRequest());
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/dispositivos"), dueno), "{}"))
                .andExpect(status().isBadRequest());
    }

    // ---- listar y desvincular ----

    @Test
    void elDuenoListaLosDeSuPlantaYUltimaCredencialEnEsNuloHastaQueSeConecte() throws Exception {
        String serial = fabricarSerial("a");
        fabricarSerial("de-nadie");
        reclamar(dueno, planta, serial).andExpect(status().isCreated());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), dueno)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serial").value(serial))
                .andExpect(jsonPath("$[0].reclamadoEn").isNotEmpty())
                .andExpect(jsonPath("$[0].ultimaCredencialEn").value((Object) null))
                .andExpect(jsonPath("$[0].secreto").doesNotExist());
        mockMvc.perform(con(get("/api/v1/plantas/" + otraPlanta + "/dispositivos"), dueno)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void desvincularResponde204YElDispositivoVuelveASinReclamarYOtraPlantaPuedeReclamarlo() throws Exception {
        String serial = fabricarSerial("a");
        reclamar(dueno, planta, serial).andExpect(status().isCreated());

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/dispositivos/" + serial), dueno))
                .andExpect(status().isNoContent());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/dispositivos"), dueno))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(con(get("/api/v1/admin/dispositivos"), admin))
                .andExpect(jsonPath("$[?(@.serial=='" + serial + "')].estado").value("SIN_RECLAMAR"));
        reclamar(dueno, otraPlanta, serial).andExpect(status().isCreated());
    }

    @Test
    void desvincularUnoQueNoEstaEnEsaPlantaEs404() throws Exception {
        String serial = fabricarSerial("a");
        reclamar(dueno, planta, serial).andExpect(status().isCreated());

        mockMvc.perform(con(delete("/api/v1/plantas/" + otraPlanta + "/dispositivos/" + serial), dueno))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("DISPOSITIVO_NO_ENCONTRADO"));
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/dispositivos/recl-no-existe"), dueno))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarLaPlantaSueltaSusDispositivosSinBorrarlos() throws Exception {
        String serial = fabricarSerial("a");
        reclamar(dueno, planta, serial).andExpect(status().isCreated());

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta), dueno)).andExpect(status().isNoContent());

        mockMvc.perform(con(get("/api/v1/admin/dispositivos"), admin))
                .andExpect(jsonPath("$[?(@.serial=='" + serial + "')].estado").value("SIN_RECLAMAR"));
    }
}
